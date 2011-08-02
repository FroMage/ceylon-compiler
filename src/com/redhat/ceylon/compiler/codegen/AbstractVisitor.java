package com.redhat.ceylon.compiler.codegen;

import java.util.Collection;

import com.redhat.ceylon.compiler.typechecker.tree.NaturalVisitor;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.Factory;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

/**
 * Abstract base class for Visitors providing access to the various 
 * Transformers, convenience methods for appending to a list of JCTrees
 * and obtaining the result after the visit.
 * 
 * @author Tom Bentley
 *
 * @param <J> The type of JCTree in the result list.
 */
public abstract class AbstractVisitor<J extends JCTree> extends Visitor implements NaturalVisitor {

    protected final CeylonTransformer gen;

    protected final StatementTransformer statementGen;

    protected final ExpressionTransformer expressionGen;

    protected final ClassTransformer classGen;
    
    private final ListBuffer<J> defs;
    
    public AbstractVisitor(CeylonTransformer gen) {
        this.gen = gen;
        this.statementGen = gen.statementGen;
        this.expressionGen = gen.expressionGen;
        this.classGen = gen.classGen;
        this.defs = new ListBuffer<J>();
    }
    
    public AbstractVisitor(CeylonTransformer gen, ListBuffer<J> defs) {
        this.gen = gen;
        this.statementGen = gen.statementGen;
        this.expressionGen = gen.expressionGen;
        this.classGen = gen.classGen;
        this.defs = defs;
    }
    
    /**
     * Gets all the results which were appended during the visit
     * @return The results
     * 
     * @see #getSingleResult()
     */
    public ListBuffer<? extends J> getResult() {
        return defs;
    }
    
    /**
     * Asserts that there's a single result, and returns it
     * @return The result
     * 
     * @see #getResult()
     */
    public <K extends J> K getSingleResult() {
        if (defs.size() != 1) {
            throw new RuntimeException();
        }
        return (K) defs.first();
    }
    
    public boolean addAll(Collection<? extends J> c) {
        return defs.addAll(c);
    }

    protected boolean add(J result) {
        return defs.add(result);
    }
    
    public ListBuffer<J> append(J x) {
        return defs.append(x);
    }

    public ListBuffer<J> appendList(List<? extends J> xs) {
        for (J x : xs) {
            append(x);
        }
        return defs;
    }
    
    public ListBuffer<J> appendList(ListBuffer<? extends J> xs) {
        for (J x : xs) {
            append(x);
        }
        return defs;
    }

    public ListBuffer<J> appendArray(J[] xs) {
        return defs.appendArray(xs);
    }
    
    protected Factory at(Node node) {
        return gen.at(node);
    }

    protected JCExpression makeIdent(String ident) {
        return gen.makeIdent(ident);
    }

    protected JCExpression makeIdent(Iterable<String> ident) {
        return gen.makeIdent(ident);
    }

    protected JCExpression makeIdent(String... ident) {
        return gen.makeIdent(ident);
    }

    protected JCExpression makeIdent(Type type) {
        return gen.makeIdent(type);
    }
    
    protected TreeMaker make() {
        return gen.make();
    }

    protected Symtab syms() {
        return gen.syms;
    }

    protected JCFieldAccess makeSelect(JCExpression s1, String s2) {
        return gen.makeSelect(s1, s2);
    }

    protected JCFieldAccess makeSelect(String s1, String s2) {
        return makeSelect(make().Ident(names().fromString(s1)), s2);
    }

    protected Name.Table names() {
        return gen.names;
    }
    
}
