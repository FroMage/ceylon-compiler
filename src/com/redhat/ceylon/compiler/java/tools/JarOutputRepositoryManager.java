/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package com.redhat.ceylon.compiler.java.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import com.redhat.ceylon.cmr.api.ArtifactContext;
import com.redhat.ceylon.cmr.api.Logger;
import com.redhat.ceylon.cmr.api.RepositoryManager;
import com.redhat.ceylon.cmr.api.SourceArchiveCreator;
import com.redhat.ceylon.cmr.ceylon.CeylonUtils;
import com.redhat.ceylon.cmr.util.JarUtils;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

public class JarOutputRepositoryManager {
    
    private Map<Module,ProgressiveJar> openJars = new HashMap<Module, ProgressiveJar>();
    private Log log;
    private Options options;
    private CeyloncFileManager ceyloncFileManager;
    
    JarOutputRepositoryManager(Log log, Options options, CeyloncFileManager ceyloncFileManager){
        this.log = log;
        this.options = options;
        this.ceyloncFileManager = ceyloncFileManager;
    }
    
    public JavaFileObject getFileObject(RepositoryManager repositoryManager, Module module, String fileName, File sourceFile) throws IOException{
        ProgressiveJar progressiveJar = getProgressiveJar(repositoryManager, module);
        return progressiveJar.getJavaFileObject(fileName, sourceFile);
    }
    
    private ProgressiveJar getProgressiveJar(RepositoryManager repositoryManager, Module module) throws IOException {
        ProgressiveJar jarFile = openJars.get(module);
        if(jarFile == null){
            jarFile = new ProgressiveJar(repositoryManager, module, log, options, ceyloncFileManager);
            openJars.put(module, jarFile);
        }
        return jarFile;
    }

    public void flush() throws IOException {
        try{
            for(ProgressiveJar jarFile : openJars.values()){
                jarFile.close();
            }
        }finally{
            // make sure we clear on return and throw, so we don't try to flush again on throw
            openJars.clear();
        }
    }
    
    static class ProgressiveJar {
        private static final String MAPPING_FILE = "META-INF/mapping.txt";
        private File originalJarFile;
        private File outputJarFile;
        private JarOutputStream jarOutputStream;
        final private Set<String> modifiedSourceFiles = new HashSet<String>();
        final private Properties writtenClassesMapping = new Properties(); 
        private Logger cmrLog;
        private Options options;
        private RepositoryManager repoManager;
        private ArtifactContext carContext;
        private SourceArchiveCreator creator;
        private Module module;
        
        public ProgressiveJar(RepositoryManager repoManager, Module module, Log log, Options options, CeyloncFileManager ceyloncFileManager) throws IOException{
            this.options = options;
            this.repoManager = repoManager;
            this.carContext = new ArtifactContext(module.getNameAsString(), module.getVersion(), ArtifactContext.CAR);
            this.cmrLog = new JavacLogger(options, Log.instance(ceyloncFileManager.getContext()));
            this.creator = CeylonUtils.makeSourceArchiveCreator(repoManager, ceyloncFileManager.getLocation(StandardLocation.SOURCE_PATH),
                    module.getNameAsString(), module.getVersion(), options.get(OptionName.VERBOSE) != null, cmrLog);
            this.module = module;
            setupJarOutput();
        }

        private void setupJarOutput() throws IOException {
            File targetJarFile = repoManager.getArtifact(carContext);
            outputJarFile = File.createTempFile("car", ".tmp");
            originalJarFile = targetJarFile;
            jarOutputStream = new JarOutputStream(new FileOutputStream(outputJarFile));
        }

        private Properties getPreviousMapping() throws IOException {
            if (originalJarFile != null) {
                JarFile jarFile = null;
                jarFile = new JarFile(originalJarFile);
                try {
                    JarEntry entry = jarFile.getJarEntry(MAPPING_FILE);
                    if (entry != null) {
                        InputStream inputStream = jarFile.getInputStream(entry);
                        try {
                            Properties previousMapping = new Properties();
                            previousMapping.load(inputStream);
                            return previousMapping;
                        } finally {
                            inputStream.close();
                        }
                    }
                } finally {
                    jarFile.close();
                }
            }
            return null;
        }

        public void close() throws IOException {
            final Set<String> copiedSourceFiles = creator.copySourceFiles(modifiedSourceFiles);

            final Properties previousMapping = getPreviousMapping();
            JarUtils.JarEntryFilter filterForCars = new JarUtils.JarEntryFilter() {
                @Override
                public boolean avoid(String entryFullName) {
                    boolean classWasUpdated = writtenClassesMapping.containsKey(entryFullName);
                    if (previousMapping != null) {
                        String sourceFileForClass = previousMapping.getProperty(entryFullName);
                        classWasUpdated = classWasUpdated || copiedSourceFiles.contains(sourceFileForClass);
                    }
                    return classWasUpdated || entryFullName.equals(MAPPING_FILE);
                }
            };
            writeMappingJarEntry(previousMapping, filterForCars);
            JarUtils.finishUpdatingJar(originalJarFile, outputJarFile, carContext, jarOutputStream, filterForCars,
                    repoManager, options.get(OptionName.VERBOSE) != null, cmrLog);
            String info;
            if(module.isDefault())
                info = module.getNameAsString();
            else
                info = module.getNameAsString() + "/" + module.getVersion();
            cmrLog.info("Created module " + info);
        }

        private void writeMappingJarEntry(Properties previousMapping, JarUtils.JarEntryFilter filter) {
            Properties newMapping = new Properties();
            newMapping.putAll(writtenClassesMapping);
            if (previousMapping != null) {
                // Add the previous mapping entries that are not related to an updated source file 
                for (String classFullName : previousMapping.stringPropertyNames() ) {
                    if (! filter.avoid(classFullName)) {
                        newMapping.setProperty(classFullName, previousMapping.getProperty(classFullName));
                    }
                }
            }
            // Write the mapping file to the Jar
            try {
                jarOutputStream.putNextEntry(new ZipEntry(MAPPING_FILE));
                newMapping.store(jarOutputStream, "");
            }
            catch(IOException e) {
                // TODO : log to the right place
            }
            finally {
                try {
                    jarOutputStream.closeEntry();
                } catch (IOException e) {
                }
            }
        }

        public JavaFileObject getJavaFileObject(String fileName, File sourceFile) {
            modifiedSourceFiles.add(sourceFile.getPath());
            // record the class file we produce so that we don't save it from the original jar
        	fileName = fileName.replace(File.separatorChar, '/');
        	addMappingEntry(fileName, JarUtils.toPlatformIndependentPath(creator.getSourcePaths(), sourceFile.getPath()));
            return new JarEntryFileObject(outputJarFile.getPath(), jarOutputStream, fileName);
        }

        private void addMappingEntry(String className,
                String sourcePath) {
            writtenClassesMapping.put(className, sourcePath);
        }
    }
}
