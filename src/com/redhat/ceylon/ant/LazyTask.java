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
package com.redhat.ceylon.ant;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;

import com.redhat.ceylon.cmr.api.Logger;
import com.redhat.ceylon.cmr.api.RepositoryManager;
import com.redhat.ceylon.cmr.api.RepositoryManagerBuilder;
import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleManager;
import com.redhat.ceylon.compiler.typechecker.context.Context;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.io.VFS;

abstract class LazyTask extends Task {
    
    private static final class NullLogger implements Logger {
        @Override
        public void error(String str) {
            // Don't care
        }

        @Override
        public void warning(String str) {
            // Don't care
        }

        @Override
        public void info(String str) {
            // Don't care
        }

        @Override
        public void debug(String str) {
            // Don't care
        }
    }

    private Path src;
    private File out;
    private List<Rep> repositories = new LinkedList<Rep>();
    private Boolean noMtimeCheck = false;

    /**
     * Sets whether a file modification time check should be performed
     * @param noMtimeCheck
     */
    public void setNoMtimeCheck(Boolean noMtimeCheck) {
        this.noMtimeCheck = noMtimeCheck;
    }

    /**
     * Set the source directories to find the source Java and Ceylon files.
     * @param src the source directories as a path
     */
    public void setSrc(Path src) {
        if (this.src == null) {
            this.src = src;
        } else {
            this.src.append(src);
        }
    }

    protected List<File> getSrc() {
        if (this.src == null) {
            return Collections.singletonList(getProject().resolveFile("source"));
        }
        String[] paths = this.src.list();
        ArrayList<File> result = new ArrayList<File>(paths.length);
        for (String path : paths) {
            result.add(getProject().resolveFile(path));
        }
        return result;
    }

    /**
     * Adds a module repository
     * @param rep the new module repository
     */
    public void addRep(Rep rep) {
        repositories.add(rep);
    }

    protected List<Rep> getRepositories() {
        return repositories;
    }

    /**
     * Set the destination directory into which the Java source files should be
     * compiled.
     * @param out the destination director
     */
    public void setOut(File out) {
        this.out = out;
    }

    protected File getOut() {
        if (this.out == null) {
            return new File(getProject().getBaseDir(), "modules");
        }
        return this.out;
    }
    
    long newestSourceFile(long mtime, File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                mtime = Math.max(mtime, newestSourceFile(mtime, child));
            }
        } else {
            long lastModified = file.lastModified();
            log(file + " last modified " + new Date(lastModified), Project.MSG_DEBUG);
            mtime = Math.max(mtime, lastModified);
        }
        return mtime;
    }
    
    long oldestOutputArtifact(long mtime, File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                mtime = Math.min(mtime, oldestOutputArtifact(mtime, child));
            }
        } else if (getArtifactFilter().accept(file)) {
            long lastModified = file.lastModified();
            log(file + " last modified " + new Date(lastModified), Project.MSG_DEBUG);
            mtime = Math.min(mtime, lastModified);
        }
        return mtime;
    }
    
    
    /**
     * Gets the module version
     * @param module The module to get the version of
     * @param srcDir The source directory
     * @return The module version, or null if no version could be found
     */
    private String getModuleVersion(Module module, File srcDir) {
        RepositoryManagerBuilder builder = new RepositoryManagerBuilder(new NullLogger());
        RepositoryManager repoManager = builder.buildRepository();
        VFS vfs = new VFS();
        Context context = new Context(repoManager, vfs);
        PhasedUnits pus = new PhasedUnits(context);
        pus.parseUnit(vfs.getFromFile(srcDir));
        for (PhasedUnit pu : pus.getPhasedUnits()) {
            pu.visitSrcModulePhase();
        }
        ModuleManager moduleManager = pus.getModuleManager();
        List<String> name = ModuleManager.splitModuleName(module.name);
        return moduleManager.getOrCreateModule(name, null).getVersion();
    }
    
    /**
     * Filters out all the modules which appear to not require 
     * compilation based on comparison of file modification times
     * @return true if everything was filtered out 
     */
    protected <M extends Module> boolean filterModules(List<M> modules) {
        if (noMtimeCheck) {
            return false;
        }
        Iterator<M> iterator = modules.iterator();
        while (iterator.hasNext()) {
            Module module = iterator.next();
            long newest = Long.MIN_VALUE;
            String version = null;
            for (File src : getSrc()) {
                version = version != null ? version : getModuleVersion(module, src);
                File srcModuleDir = new File(src, module.toDir().getPath());
                newest = newestSourceFile(newest, srcModuleDir);
                log("Newest file in " + srcModuleDir + " " + new Date(newest), Project.MSG_DEBUG);
            }
            if (version == null) {
                log("Unable to determine version (and hence timestamp) of " + module, Project.MSG_VERBOSE);
                continue;
            }
            File outModuleDir = getArtifactDir(version, module);
            long oldest = oldestOutputArtifact(Long.MAX_VALUE, outModuleDir);
            log("Oldest file in " + outModuleDir + " " + new Date(oldest), Project.MSG_DEBUG);
            if (newest != Long.MIN_VALUE
                    && oldest != Long.MAX_VALUE
                    && newest < oldest) {
                log("No need to compile " + module + ", it's up to date");
                iterator.remove();
            }
        }
        return modules.size() == 0;
    }
    
    /**
     * Filters out all the source files which appear to not require 
     * compilation based on comparison of file modification times
     * @return true if everything was filtered out 
     */
    protected boolean filterFiles(List<File> files) {
        if (noMtimeCheck) {
            return false;
        }
        long newestFile = Long.MIN_VALUE;
        String version = null;
        Iterator<File> iter = files.iterator();
        while (iter.hasNext()) {
            File file = iter.next();
            Module module = inferModule(file);
            if (module == null) {
                log("Unable to determine module of " + file, Project.MSG_VERBOSE);
                continue;
            }
            for (File src : getSrc()) {
                version = version != null ? version : getModuleVersion(module, src);
            }
            if (version == null) {
                log("Unable to determine version (and hence timestamp) of " + module, Project.MSG_VERBOSE);
                continue;
            }
            File outModuleDir = getArtifactDir(version, module);
            long oldest = oldestOutputArtifact(Long.MAX_VALUE, outModuleDir);
            log("Oldest file in " + outModuleDir + " " + new Date(oldest), Project.MSG_DEBUG);
            
            newestFile = Math.max(newestFile, file.lastModified());
            log("File " + file + " last modified " + new Date(newestFile), Project.MSG_DEBUG);
            if (newestFile != Long.MIN_VALUE
                    && oldest != Long.MAX_VALUE
                    && newestFile < oldest) {
                log("No need to compile " + file + ", it's up to date");
                iter.remove();
            }
        }

        return files.size() == 0;
    }

    protected abstract File getArtifactDir(String version, Module module);
    
    protected abstract FileFilter getArtifactFilter();

    private Module inferModule(File file) {
        if (file.exists()) {
            for (File src : getSrc()) {
                if (file.getAbsolutePath().startsWith(src.getAbsolutePath())) {
                    while (!file.equals(src)) {
                        File moduleDescriptor = file.isDirectory() ? new File(file, "module.ceylon") : file;
                        if (moduleDescriptor.exists()
                                && moduleDescriptor.getName().equals("module.ceylon")) {
                            String moduleName = moduleDescriptor.getParentFile().getAbsolutePath().substring(src.getAbsolutePath().length()+1).replace(File.separator, ".");
                            Module module = new Module();
                            module.setName(moduleName);
                            return module;
                        }
                        file = file.getParentFile();
                    }
                }
            }
        }
        return null;
    }
    
}
