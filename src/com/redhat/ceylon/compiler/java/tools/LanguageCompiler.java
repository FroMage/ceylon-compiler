/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 */

package com.redhat.ceylon.compiler.java.tools;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

import javax.annotation.processing.Processor;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;

import com.redhat.ceylon.compiler.java.codegen.CeylonClassWriter;
import com.redhat.ceylon.compiler.java.codegen.CeylonCompilationUnit;
import com.redhat.ceylon.compiler.java.codegen.CeylonFileObject;
import com.redhat.ceylon.compiler.java.codegen.CeylonTransformer;
import com.redhat.ceylon.compiler.java.loader.CeylonEnter;
import com.redhat.ceylon.compiler.java.loader.CeylonModelLoader;
import com.redhat.ceylon.compiler.java.loader.model.CompilerModuleManager;
import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleManager;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.io.VFS;
import com.redhat.ceylon.compiler.typechecker.io.VirtualFile;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Modules;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer;
import com.redhat.ceylon.compiler.typechecker.parser.CeylonParser;
import com.redhat.ceylon.compiler.typechecker.parser.LexError;
import com.redhat.ceylon.compiler.typechecker.parser.ParseError;
import com.redhat.ceylon.compiler.typechecker.parser.RecognitionError;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.util.ModuleManagerFactory;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.jvm.ClassWriter;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.SourceLanguage.Language;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.JavacFileManager;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.Pair;
import com.sun.tools.javac.util.Position;
import com.sun.tools.javac.util.Position.LineMap;

public class LanguageCompiler extends JavaCompiler {

    /** The context key for the phasedUnits. */
    protected static final Context.Key<PhasedUnits> phasedUnitsKey = new Context.Key<PhasedUnits>();

    /** The context key for the ceylon context. */
    protected static final Context.Key<com.redhat.ceylon.compiler.typechecker.context.Context> ceylonContextKey = new Context.Key<com.redhat.ceylon.compiler.typechecker.context.Context>();

    private final CeylonTransformer gen;
    private final PhasedUnits phasedUnits;
    private final com.redhat.ceylon.compiler.typechecker.context.Context ceylonContext;
    private final VFS vfs;

	private CeylonModelLoader modelLoader;

    private CeylonEnter ceylonEnter;

    private Options options;

    /** Get the PhasedUnits instance for this context. */
    public static PhasedUnits getPhasedUnitsInstance(final Context context) {
        PhasedUnits phasedUnits = context.get(phasedUnitsKey);
        if (phasedUnits == null) {
            com.redhat.ceylon.compiler.typechecker.context.Context ceylonContext = getCeylonContextInstance(context);
            phasedUnits = new PhasedUnits(ceylonContext, new ModuleManagerFactory(){
                @Override
                public ModuleManager createModuleManager(com.redhat.ceylon.compiler.typechecker.context.Context ceylonContext) {
                    return new CompilerModuleManager(ceylonContext, context);
                }
            });
            context.put(phasedUnitsKey, phasedUnits);
        }
        return phasedUnits;
    }

    /** Get the Ceylon context instance for this context. */
    public static com.redhat.ceylon.compiler.typechecker.context.Context getCeylonContextInstance(Context context) {
        com.redhat.ceylon.compiler.typechecker.context.Context ceylonContext = context.get(ceylonContextKey);
        if (ceylonContext == null) {
            CeyloncFileManager fileManager = (CeyloncFileManager) context.get(JavaFileManager.class);
            VFS vfs = new VFS();
            ceylonContext = new com.redhat.ceylon.compiler.typechecker.context.Context(fileManager.getRepositoryManager(), vfs);
            context.put(ceylonContextKey, ceylonContext);
        }
        return ceylonContext;
    }

    /** Get the JavaCompiler instance for this context. */
    public static JavaCompiler instance(Context context) {
        Options options = Options.instance(context);
        options.put("-Xprefer", "source");
        // make sure it's registered
        CeylonLog.instance(context);
        CeylonEnter.instance(context);
        CeylonClassWriter.instance(context);
        JavaCompiler instance = context.get(compilerKey);
        if (instance == null)
            instance = new LanguageCompiler(context);
        return instance;
    }

    public LanguageCompiler(Context context) {
        super(context);
        ceylonContext = getCeylonContextInstance(context);
        vfs = ceylonContext.getVfs();
        phasedUnits = getPhasedUnitsInstance(context);
        try {
            gen = CeylonTransformer.getInstance(context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        modelLoader = CeylonModelLoader.instance(context);
        ceylonEnter = CeylonEnter.instance(context);
        options = Options.instance(context);
    }

    /**
     * Parse contents of file.
     * @param filename The name of the file to be parsed.
     */
    public JCTree.JCCompilationUnit parse(JavaFileObject filename) {
        JavaFileObject prev = log.useSource(filename);
        try {
            JCTree.JCCompilationUnit t;
            if (filename.getName().endsWith(".java")) {

                t = parse(filename, readSource(filename));
            } else {
                t = ceylonParse(filename, readSource(filename));
            }
            if (t.endPositions != null)
                log.setEndPosTable(filename, t.endPositions);
            return t;
        } finally {
            log.useSource(prev);
        }
    }

    protected JCCompilationUnit parse(JavaFileObject filename, CharSequence readSource) {
        // FIXME
        if (filename instanceof CeylonFileObject)
            return ceylonParse(filename, readSource);
        else
            return super.parse(filename, readSource);
    }

    private JCCompilationUnit ceylonParse(JavaFileObject filename, CharSequence readSource) {
        if(ceylonEnter.hasRun())
            throw new RuntimeException("Trying to load new source file after CeylonEnter has been called: "+filename);
        try {
            String source = readSource.toString();
            ANTLRStringStream input = new ANTLRStringStream(source);
            CeylonLexer lexer = new CeylonLexer(input);

            CommonTokenStream tokens = new CommonTokenStream(lexer);

            CeylonParser parser = new CeylonParser(tokens);
            CompilationUnit cu = parser.compilationUnit();

            char[] chars = source.toCharArray();
            LineMap map = Position.makeLineMap(chars, chars.length, false);

            java.util.List<LexError> lexerErrors = lexer.getErrors();
            for (LexError le : lexerErrors) {
                printError(le, le.getMessage(), "ceylon.lexer", map);
            }

            java.util.List<ParseError> parserErrors = parser.getErrors();
            for (ParseError pe : parserErrors) {
                printError(pe, pe.getMessage(), "ceylon.parser", map);
            }

            if (lexer.getNumberOfSyntaxErrors() != 0) {
                log.error("ceylon.lexer.failed");
            } else if (parser.getNumberOfSyntaxErrors() != 0) {
                log.error("ceylon.parser.failed");
            } else {
                ModuleManager moduleManager = phasedUnits.getModuleManager();
                File sourceFile = new File(filename.toString());
                // FIXME: temporary solution
                VirtualFile file = vfs.getFromFile(sourceFile);
                VirtualFile srcDir = vfs.getFromFile(getSrcDir(sourceFile));
                // FIXME: this is bad in many ways
                String pkgName = getPackage(filename);
                // make a Package with no module yet, we will resolve them later
                com.redhat.ceylon.compiler.typechecker.model.Package p = modelLoader.findOrCreatePackage(null, pkgName == null ? "" : pkgName);
                PhasedUnit phasedUnit = new CeylonPhasedUnit(file, srcDir, cu, p, moduleManager, ceylonContext, filename, map);
                phasedUnits.addPhasedUnit(file, phasedUnit);
                gen.setMap(map);

                return gen.makeJCCompilationUnitPlaceholder(cu, filename, pkgName, phasedUnit);
            }
        } catch (Exception e) {
            log.error("ceylon", e.getMessage());
        }

        JCCompilationUnit result = make.TopLevel(List.<JCAnnotation> nil(), null, List.<JCTree> of(make.Erroneous()));
        result.sourcefile = filename;
        return result;
    }

    @Override
    public List<JCCompilationUnit> parseFiles(List<JavaFileObject> fileObjects)
            throws IOException {
        List<JCCompilationUnit> trees = super.parseFiles(fileObjects);
        LinkedList<JCCompilationUnit> moduleTrees = new LinkedList<JCCompilationUnit>();
        loadCompiledModules(trees, moduleTrees);
        for (JCCompilationUnit moduleTree : moduleTrees) {
            trees = trees.append(moduleTree);
        }
        return trees;
    }

    private void loadCompiledModules(List<JCCompilationUnit> trees, LinkedList<JCCompilationUnit> moduleTrees) {
        phasedUnits.visitModules();
        Modules modules = ceylonContext.getModules();
        // now make sure the phase units have their modules and packages set correctly
        for (PhasedUnit pu : phasedUnits.getPhasedUnits()) {
            Package pkg = pu.getPackage();
            loadModuleFromSource(pkg, modules, moduleTrees);
        }
        // also make sure we have packages and modules set up for every Java file we compile
        for(JCCompilationUnit cu : trees){
            // skip Ceylon CUs
            if(cu instanceof CeylonCompilationUnit)
                continue;
            String packageName = ""; 
            if(cu.pid != null)
                packageName = TreeInfo.fullName(cu.pid).toString();
            Package pkg = modelLoader.findOrCreatePackage(null, packageName);
            loadModuleFromSource(pkg, modules, moduleTrees);
        }
    }

    private void loadModuleFromSource(Package pkg, Modules modules, LinkedList<JCCompilationUnit> moduleTrees) {
        // skip it if we already resolved the package
        if(pkg.getModule() != null)
            return;
        String pkgName = pkg.getQualifiedNameString();
        Module module = null;
        // do we have a module for this package?
        // FIXME: is this true? what if we have a module.ceylon at toplevel?
        if(pkgName.isEmpty())
            module = modules.getDefaultModule();
        else{
            for(Module m : modules.getListOfModules()){
                if(pkgName.startsWith(m.getNameAsString())){
                    module = m;
                    break;
                }
            }
            if(module == null){
                module = loadModuleFromSource(pkgName, moduleTrees);
            }
            else if (! module.isAvailable()) {
                loadModuleFromSource(pkgName, moduleTrees);
            }

            if(module == null){
                // no declaration for it, must be the default module
                module = modules.getDefaultModule();
            }
        }
        // bind module and package together
        pkg.setModule(module);
        module.getPackages().add(pkg);
        // automatically add this module's jar to the classpath if it exists
        ceylonEnter.addOutputModuleToClassPath(module);
    }

    private Module loadModuleFromSource(String pkgName, LinkedList<JCCompilationUnit> moduleTrees) {
        if(pkgName.isEmpty())
            return null;
        String moduleClassName = pkgName + ".module";
        JavaFileObject fileObject;
        try {
            if(options.get(OptionName.VERBOSE) != null){
                Log.printLines(log.noticeWriter, "[Trying to load module "+moduleClassName+"]");
            }
            fileObject = fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, moduleClassName, Kind.SOURCE);
            if(options.get(OptionName.VERBOSE) != null){
                Log.printLines(log.noticeWriter, "[Got file object: "+fileObject+"]");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return loadModuleFromSource(getParentPackage(pkgName), moduleTrees);
        }
        if(fileObject != null){
            CeylonCompilationUnit ceylonCompilationUnit = (CeylonCompilationUnit) parse(fileObject);
            moduleTrees.add(ceylonCompilationUnit);
            // parse the module info from there
            Module module = ceylonCompilationUnit.phasedUnit.visitSrcModulePhase();
            ceylonCompilationUnit.phasedUnit.visitRemainingModulePhase();
            // now try to obtain the parsed module
            if(module != null){
                ceylonCompilationUnit.phasedUnit.getPackage().setModule(module);
                return module;
            }
        }
        return loadModuleFromSource(getParentPackage(pkgName), moduleTrees);
    }

    private String getParentPackage(String pkgName) {
        int lastDot = pkgName.lastIndexOf(".");
        if(lastDot == -1)
            return "";
        return pkgName.substring(0, lastDot);
    }

    // FIXME: this function is terrible, possibly refactor it with getPackage?
    private File getSrcDir(File sourceFile) throws IOException {
        String name;
        try {
            name = sourceFile.getCanonicalPath();
        } catch (IOException e) {
            // FIXME
            throw new RuntimeException(e);
        }
        Iterable<? extends File> prefixes = ((JavacFileManager)fileManager).getLocation(StandardLocation.SOURCE_PATH);
        
        int maxPrefixLength = 0;
        File srcDirFile = null;
        for (File prefixFile : prefixes) {
            String path = prefixFile.getCanonicalPath();
            if (name.startsWith(path) && path.length() > maxPrefixLength) {
                maxPrefixLength = path.length();
                srcDirFile = prefixFile;
            }
        }
        if (srcDirFile != null) {
            return srcDirFile;
        }
        throw new RuntimeException(name+ " is not in the current source path: "+((JavacFileManager)fileManager).getLocation(StandardLocation.SOURCE_PATH));
    }

    private String getPackage(JavaFileObject file) throws IOException{
        Iterable<? extends File> prefixes = ((JavacFileManager)fileManager).getLocation(StandardLocation.SOURCE_PATH);

    	// Figure out the package name by stripping the "-src" prefix and
    	// extracting
    	// the package part of the fullname.
        
        String filePath = file.toUri().getPath();
        // go absolute
        filePath = new File(filePath).getCanonicalPath();
        
        int srcDirLength = 0;
        for (File prefixFile : prefixes) {
            String prefix = prefixFile.getCanonicalPath();
            if (filePath.startsWith(prefix) && prefix.length() > srcDirLength) {
                srcDirLength = prefix.length();
            }
    	}
        
        if (srcDirLength > 0) {
            String fullname = filePath.substring(srcDirLength);
            assert fullname.endsWith(".ceylon");
            fullname = fullname.substring(0, fullname.length() - ".ceylon".length());
            fullname = fullname.replace(File.separator, ".");
            if(fullname.startsWith("."))
                fullname = fullname.substring(1);
            String packageName = Convert.packagePart(fullname);
            if (!packageName.equals(""))
                return packageName;
        }
    	return null;
    }
    
    private void printError(RecognitionError le, String message, String key, LineMap map) {
        int pos = -1;
        if (le.getLine() > 0) {
        	pos = map.getStartPosition(le.getLine()) + le.getCharacterInLine();
        }
        log.error(pos, key, message);
    }

    public Env<AttrContext> attribute(Env<AttrContext> env) {
        if (env.toplevel.sourcefile instanceof CeylonFileObject) {
            try {
                Context.SourceLanguage.push(Language.CEYLON);
                return super.attribute(env);
            } finally {
                Context.SourceLanguage.pop();
            }
        }
        return super.attribute(env);
    }

    @Override
    protected JavaFileObject genCode(Env<AttrContext> env, JCClassDecl cdef) throws IOException {
        if (env.toplevel.sourcefile instanceof CeylonFileObject) {
            try {
                Context.SourceLanguage.push(Language.CEYLON);
                // call our own genCode
                return genCodeUnlessError(env, cdef);
            } finally {
                Context.SourceLanguage.pop();
            }
        }
        return super.genCode(env, cdef);
    }

    @Override
    protected boolean shouldStop(CompileState cs) {
        // we override this to make sure we don't stop because of errors, because we want to generate
        // code for classes with no errors
        if (shouldStopPolicy == null)
            return false;
        else
            return cs.ordinal() > shouldStopPolicy.ordinal();
    }

    private JavaFileObject genCodeUnlessError(Env<AttrContext> env, JCClassDecl cdef) throws IOException {
        try {
            CeylonFileObject sourcefile = (CeylonFileObject) env.toplevel.sourcefile;
            // do not look at the global number of errors but only those for this file
            if (super.gen.genClass(env, cdef) && sourcefile.errors == 0)
                return writer.writeClass(cdef.sym);
        } catch (ClassWriter.PoolOverflow ex) {
            log.error(cdef.pos(), "limit.pool");
        } catch (ClassWriter.StringOverflow ex) {
            log.error(cdef.pos(), "limit.string.overflow",
                    ex.value.substring(0, 20));
        } catch (CompletionFailure ex) {
            chk.completionError(cdef.pos(), ex);
        }
        return null;
    }

    protected void desugar(final Env<AttrContext> env, Queue<Pair<Env<AttrContext>, JCClassDecl>> results) {
        if (env.toplevel.sourcefile instanceof CeylonFileObject) {
            try {
                Context.SourceLanguage.push(Language.CEYLON);
                super.desugar(env, results);
                return;
            } finally {
                Context.SourceLanguage.pop();
            }
        }
        super.desugar(env, results);
    }
    
    protected void flow(Env<AttrContext> env, Queue<Env<AttrContext>> results) {
        if (env.toplevel.sourcefile instanceof CeylonFileObject) {
            try {
                Context.SourceLanguage.push(Language.CEYLON);
                super.flow(env, results);
                return;
            } finally {
                Context.SourceLanguage.pop();
            }
        }
        super.flow(env, results);   
    }

    @Override
    public void initProcessAnnotations(Iterable<? extends Processor> processors)
            throws IOException {
        // don't do anything, which will leave the "processAnnotations" field to false
    }

}
