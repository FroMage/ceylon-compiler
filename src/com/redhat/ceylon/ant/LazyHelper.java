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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.Project;

import com.redhat.ceylon.common.ant.Module;

abstract class LazyHelper {

    private final LazyTask task;
    
    public LazyHelper(LazyTask task) {
        this.task = task;
    }

    
    long newestSourceFile(long mtime, File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                mtime = Math.max(mtime, newestSourceFile(mtime, child));
            }
        } else {
            long lastModified = file.lastModified();
            task.log(file + " last modified " + new Date(lastModified), Project.MSG_DEBUG);
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
            task.log(file + " last modified " + new Date(lastModified), Project.MSG_DEBUG);
            mtime = Math.min(mtime, lastModified);
        }
        return mtime;
    }
    
    /**
     * Filters out all the modules which appear to not require 
     * compilation based on comparison of file modification times
     * @return true if everything was filtered out 
     */
    protected <M extends Module> boolean filterModules(List<M> modules) {
        if (task.getNoMtimeCheck() || isOutputRepositoryURL()) {
            return false;
        }
        Iterator<M> iterator = modules.iterator();
        while (iterator.hasNext()) {
            Module module = iterator.next();
            long newest = Long.MIN_VALUE;
            String version = null;
            for (File src : task.getSrc()) {
                version = version != null ? version : new ModuleDescriptorReader(module, src).getModuleVersion();
                File srcModuleDir = new File(src, module.toDir().getPath());
                newest = newestSourceFile(newest, srcModuleDir);
                task.log("Newest file in " + srcModuleDir + " " + new Date(newest), Project.MSG_DEBUG);
            }
            if (version == null) {
                task.log("Unable to determine version (and hence timestamp) of " + module, Project.MSG_VERBOSE);
                continue;
            }
            File outModuleDir = getArtifactDir(version, module);
            long oldest = oldestOutputArtifact(Long.MAX_VALUE, outModuleDir);
            task.log("Oldest file in " + outModuleDir + " " + new Date(oldest), Project.MSG_DEBUG);
            if (newest != Long.MIN_VALUE
                    && oldest != Long.MAX_VALUE
                    && newest < oldest) {
                task.log("No need to compile " + module + ", it's up to date");
                iterator.remove();
            }
        }
        return modules.size() == 0;
    }
    
    private boolean isOutputRepositoryURL() {
        String out = task.getOut();
        if(out == null || out.isEmpty())
            return false;
        try{
            new URL(out);
            return true;
        }catch(MalformedURLException x){
            return false;
        }
    }

    /**
     * Filters out all the source files which appear to not require 
     * compilation based on comparison of file modification times
     * @return true if everything was filtered out 
     */
    protected boolean filterFiles(List<File> files) {
        if (task.getNoMtimeCheck() || isOutputRepositoryURL()) {
            return false;
        }
        long newestFile = Long.MIN_VALUE;
        String version = null;
        Iterator<File> iter = files.iterator();
        while (iter.hasNext()) {
            File file = iter.next();
            Module module = inferModule(file);
            if (module == null) {
                task.log("Unable to determine module of " + file, Project.MSG_VERBOSE);
                continue;
            }
            for (File src : task.getSrc()) {
                version = version != null ? version : new ModuleDescriptorReader(module, src).getModuleVersion();
            }
            if (version == null) {
                task.log("Unable to determine version (and hence timestamp) of " + module, Project.MSG_VERBOSE);
                continue;
            }
            File outModuleDir = getArtifactDir(version, module);
            long oldest = oldestOutputArtifact(Long.MAX_VALUE, outModuleDir);
            task.log("Oldest file in " + outModuleDir + " " + new Date(oldest), Project.MSG_DEBUG);
            
            newestFile = Math.max(newestFile, file.lastModified());
            task.log("File " + file + " last modified " + new Date(newestFile), Project.MSG_DEBUG);
            if (newestFile != Long.MIN_VALUE
                    && oldest != Long.MAX_VALUE
                    && newestFile < oldest) {
                task.log("No need to compile " + file + ", it's up to date");
                iter.remove();
            }
        }

        return files.size() == 0;
    }

    protected abstract File getArtifactDir(String version, Module module);
    
    protected abstract FileFilter getArtifactFilter();

    private Module inferModule(File file) {
        if (file.exists()) {
            for (File src : task.getSrc()) {
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
