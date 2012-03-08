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

package com.redhat.ceylon.compiler.java.loader;

import javax.tools.JavaFileObject.Kind;

import com.redhat.ceylon.cmr.api.ArtifactResult;
import com.redhat.ceylon.compiler.java.codegen.CeylonCompilationUnit;
import com.redhat.ceylon.compiler.java.loader.mirror.JavacClass;
import com.redhat.ceylon.compiler.java.loader.mirror.JavacMethod;
import com.redhat.ceylon.compiler.java.loader.model.CompilerModuleManager;
import com.redhat.ceylon.compiler.java.tools.CeylonLog;
import com.redhat.ceylon.compiler.java.tools.LanguageCompiler;
import com.redhat.ceylon.compiler.java.util.Util;
import com.redhat.ceylon.compiler.loader.AbstractModelLoader;
import com.redhat.ceylon.compiler.loader.TypeParser;
import com.redhat.ceylon.compiler.loader.mirror.ClassMirror;
import com.redhat.ceylon.compiler.loader.mirror.MethodMirror;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Declaration;
import com.sun.tools.javac.code.Scope.Entry;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Name.Table;
import com.sun.tools.javac.util.Options;

public class CeylonModelLoader extends AbstractModelLoader {
    
    private Symtab symtab;
    private Table names;
    private ClassReader reader;
    private PhasedUnits phasedUnits;
    private com.redhat.ceylon.compiler.typechecker.context.Context ceylonContext;
    private Log log;
    private Types types;
    private Options options;
    
    public static CeylonModelLoader instance(Context context) {
        CeylonModelLoader instance = context.get(CeylonModelLoader.class);
        if (instance == null) {
            instance = new CeylonModelLoader(context);
            context.put(CeylonModelLoader.class, instance);
        }
        return instance;
    }

    public CeylonModelLoader(Context context) {
        phasedUnits = LanguageCompiler.getPhasedUnitsInstance(context);
        ceylonContext = LanguageCompiler.getCeylonContextInstance(context);
        symtab = Symtab.instance(context);
        names = Name.Table.instance(context);
        reader = CeylonClassReader.instance(context);
        log = CeylonLog.instance(context);
        types = Types.instance(context);
        typeFactory = TypeFactory.instance(context);
        typeParser = new TypeParser(this, typeFactory);
        options = Options.instance(context);
        isBootstrap = options.get(OptionName.BOOTSTRAPCEYLON) != null;
        moduleManager = phasedUnits.getModuleManager();
        modules = ceylonContext.getModules();
    }

    @Override
    public void addModuleToClassPath(Module module, ArtifactResult artifact){
        if(artifact != null)
            ((CompilerModuleManager)phasedUnits.getModuleManager()).getCeylonEnter().addModuleToClassPath(module, true, artifact);
    }

    public void setupSourceFileObjects(com.sun.tools.javac.util.List<JCCompilationUnit> trees) {
        for(final JCCompilationUnit tree : trees){
            if (!(tree instanceof CeylonCompilationUnit)) {
                continue;
            }
            CompilationUnit ceylonTree = ((CeylonCompilationUnit)tree).ceylonTree;
            final String pkgName = tree.getPackageName() != null ? tree.getPackageName().toString() : "";
            ceylonTree.visit(new SourceDeclarationVisitor(){
                @Override
                public void loadFromSource(Declaration decl) {
                    String name = Util.quoteIfJavaKeyword(decl.getIdentifier().getText());
                    String fqn = pkgName.isEmpty() ? name : pkgName+"."+name;
                    try{
                        reader.enterClass(names.fromString(fqn), tree.getSourceFile());
                    }catch(AssertionError error){
                        // this happens when we have already registered a source file for this decl, so let's
                        // print out a helpful message
                        // see https://github.com/ceylon/ceylon-compiler/issues/250
                        ClassMirror previousClass = lookupClassMirror(fqn);
                        log.error("ceylon", "Duplicate declaration error: "+fqn+" is declared twice: once in "
                                +tree.getSourceFile()+" and again in: "+
                                (previousClass != null ? ((JavacClass)previousClass).classSymbol.classfile : "another file"));
                    }
                }
            });
        }
        // If we're bootstrapping the Ceylon language now load the symbols from the source CU
        if(isBootstrap)
            symtab.loadCeylonSymbols();
    }
    
    @Override
    public void loadPackage(String packageName, boolean loadDeclarations) {
        // abort if we already loaded it, but only record that we loaded it if we want
        // to load the declarations, because merely calling complete() on the package
        // is OK
        packageName = Util.quoteJavaKeywords(packageName);
        if(loadDeclarations && !loadedPackages.add(packageName)){
            return;
        }
        PackageSymbol ceylonPkg = packageName.equals("") ? syms().unnamedPackage : reader.enterPackage(names.fromString(packageName));
        ceylonPkg.complete();
        if(loadDeclarations){
            /*
             * Eventually this will go away as we get a hook from the typechecker to load on demand, but
             * for now the typechecker requires at least ceylon.language to be loaded 
             */
            for(Symbol m : ceylonPkg.members().getElements()){
                if(!(m instanceof ClassSymbol))
                    return;
                ClassSymbol enclosingClass = getEnclosing((ClassSymbol) m);
                if(enclosingClass.classfile.getKind() != Kind.SOURCE)
                    convertToDeclaration(lookupClassMirror(m.getQualifiedName().toString()), DeclarationType.VALUE);
            }
        }
    }

    private ClassSymbol getEnclosing(ClassSymbol c) {
        Symbol owner = c.owner;
        com.sun.tools.javac.util.List<Name> enclosing = Convert.enclosingCandidates(Convert.shortName(c.name));
        if(enclosing.isEmpty())
            return c;
        Name name = enclosing.head;
        Symbol encl = owner.members().lookup(name).sym;
        if (encl == null || !(encl instanceof ClassSymbol))
            encl = symtab.classes.get(TypeSymbol.formFlatName(name, owner));
        if(encl != null)
            return (ClassSymbol) encl;
        return c;
    }

    @Override
    public ClassMirror lookupNewClassMirror(String name) {
        ClassSymbol classSymbol;

        String outerName = name;
        /*
         * This madness here tries to look for a class, and if it fails, tries to resolve it 
         * from its parent class. This is required because a.b.C.D (where D is an inner class
         * of C) is not found in symtab.classes but in C's ClassSymbol.enclosedElements.
         */
        do{
            classSymbol = symtab.classes.get(names.fromString(outerName));
            if(classSymbol != null){
                if(outerName.length() != name.length())
                    classSymbol = lookupInnerClass(classSymbol, name.substring(outerName.length()+1).split("\\."));
                return classSymbol != null ? new JavacClass(classSymbol) : null;
            }
            int lastDot = outerName.lastIndexOf(".");
            if(lastDot == -1 || lastDot == 0)
                return null;
            outerName = outerName.substring(0, lastDot);
        }while(classSymbol == null);
        return null;
    }

    private ClassSymbol lookupInnerClass(ClassSymbol classSymbol, String[] parts) {
        PART:
            for(String part : parts){
                for(Symbol s : classSymbol.getEnclosedElements()){
                    if(s instanceof ClassSymbol 
                            && s.getSimpleName().toString().equals(part)){
                        classSymbol = (ClassSymbol) s;
                        continue PART;
                    }
                }
                // didn't find the inner class
                return null;
            }
        return classSymbol;
    }

    private MethodSymbol getOverriddenMethod(MethodSymbol method, Types types) {
        MethodSymbol impl = null;
        // interfaces have a different way to work
        if(method.owner.isInterface())
            return (MethodSymbol) method.implemented(method.owner.type.tsym, types);
        for (Type superType = types.supertype(method.owner.type);
                impl == null && superType.tsym != null;
                superType = types.supertype(superType)) {
            TypeSymbol i = superType.tsym;
            // never go above this type since it has no supertype in Ceylon (does in Java though)
            if(i.getQualifiedName().toString().equals("ceylon.language.Void"))
                break;
            for (Entry e = i.members().lookup(method.name);
                    impl == null && e.scope != null;
                    e = e.next()) {
                if (method.overrides(e.sym, (TypeSymbol)method.owner, types, true) &&
                        // FIXME: I suspect the following requires a
                        // subst() for a parametric return type.
                        types.isSameType(method.type.getReturnType(),
                                types.memberType(method.owner.type, e.sym).getReturnType())) {
                    impl = (MethodSymbol) e.sym;
                }
            }
            // try in the interfaces
            if(impl == null)
                impl = (MethodSymbol) method.implemented(i, types);
        }
        // try in the interfaces
        if(impl == null)
            impl = (MethodSymbol) method.implemented(method.owner.type.tsym, types);
        return impl;
    }

    public Symtab syms() {
        return symtab;
    }

    @Override
    protected void logVerbose(String message) {
        if(options.get(OptionName.VERBOSE) != null || options.get(OptionName.VERBOSE + ":loader") != null){
            Log.printLines(log.noticeWriter, message);
        }
    }

    @Override
    protected void logWarning(String message) {
        log.warning("ceylon", message);
    }

    @Override
    protected void logError(String message) {
        log.error("ceylon", message);
    }

    @Override
    protected boolean isOverridingMethod(MethodMirror methodSymbol) {
        return getOverriddenMethod(((JavacMethod)methodSymbol).methodSymbol, types) != null;
    }
}
