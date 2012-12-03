package com.redhat.ceylon.compiler.java.loader;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Expression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.InvocationExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SpecifierExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SpecifierStatement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Term;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

/**
 * Determines if a value is "captured" by 
 * block nested in the same containing scope.
 * 
 * For example, a captured value in a class
 * body is an attribute. A captured value in
 * a method body can outlive the execution of
 * the method.
 * 
 * @author Gavin King
 *
 */
public class ValueVisitor extends Visitor {
    
    private TypedDeclaration declaration;
    private boolean inCapturingScope = false;
    
    public ValueVisitor(TypedDeclaration declaration) {
        this.declaration = declaration;
    }
    
    private boolean enterCapturingScope() {
        boolean cs = inCapturingScope;
        inCapturingScope = true;
        return cs;
    }
    
    private void exitCapturingScope(boolean cs) {
        inCapturingScope = cs;
    }
    
    @Override public void visit(Tree.BaseMemberExpression that) {
        visitReference(that);
        /*if (that.getIdentifier()!=null) {
            TypedDeclaration d = (TypedDeclaration) getDeclaration(that.getScope(), that.getUnit(), that.getIdentifier(), context);
            visitReference(that, d);
        }*/
    }

    private void visitReference(Tree.Primary that) {
        if (inCapturingScope) {
            capture(that);
        }
    }

    private void capture(Tree.Primary that) {
        capture(that, false);
    }
    
    private void capture(Tree.Primary that, boolean methodSpecifier) {
        if (that instanceof Tree.MemberOrTypeExpression) {
            final Declaration decl = ((Tree.MemberOrTypeExpression) that).getDeclaration();
            if (!(decl instanceof TypedDeclaration)) {
                return;
            }
            TypedDeclaration d = (TypedDeclaration) decl;
            if (d==declaration) {
                if (d instanceof Value) {
                    ((Value) d).setCaptured(true);
                }
                else if (d instanceof Parameter) {
                    if (!d.getContainer().equals(that.getScope())) { //a reference from a default argument 
                                                                     //expression of the same parameter 
                                                                     //list does not capture a parameter
                        ((Parameter) d).setCaptured(true);
                    } else if (methodSpecifier) {
                        ((Parameter) d).setCaptured(true);
                    }
                }
                /*if (d.isVariable() && !d.isClassMember() && !d.isToplevel()) {
                    that.addError("access to variable local from capturing scope: " + declaration.getName());
                }*/
            }
        }
    }
    
    @Override
    public void visit(Tree.QualifiedMemberExpression that) {
        super.visit(that);
        if (isSelfReference(that.getPrimary())) {
            visitReference(that);
        }
        else {
            capture(that);
        }
    }

    private boolean isSelfReference(Tree.Primary that) {
        return that instanceof Tree.This || that instanceof Tree.Outer;
    }

    @Override public void visit(Tree.Declaration that) {
        Declaration dm = that.getDeclarationModel();
        if (dm==declaration.getContainer() 
                || dm==declaration
                || (dm instanceof Setter && ((Setter) dm).getGetter()==declaration)) {
            inCapturingScope = false;
        }
        super.visit(that);
    }
    
    @Override public void visit(Tree.ClassDefinition that) {
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }
    
    @Override public void visit(Tree.ObjectDefinition that) {
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }
    
    @Override public void visit(Tree.MethodDefinition that) {
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }
    
    @Override public void visit(Tree.AttributeGetterDefinition that) {
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }
    
    @Override public void visit(Tree.AttributeSetterDefinition that) {
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }
    
    @Override public void visit(Tree.ObjectArgument that) {
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }
    
    @Override public void visit(Tree.MethodArgument that) {
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }
    
    @Override public void visit(Tree.AttributeArgument that) {
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }
    
    @Override public void visit(Tree.DefaultArgument that) {
        //parameter declarations capture 
        //their default argument
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }    
    
    @Override public void visit(Tree.FunctionArgument that) {
        boolean cs = enterCapturingScope();
        super.visit(that);
        exitCapturingScope(cs);
    }
    
    @Override public void visit(Tree.MethodDeclaration that) {
        super.visit(that);
        final SpecifierExpression specifier = that.getSpecifierExpression();
        if (specifier != null && specifier instanceof Tree.LazySpecifierExpression) {
            boolean cs = enterCapturingScope();
            specifier.visit(this);
            exitCapturingScope(cs);
        }   
        
    }

    @Override public void visit(Tree.Comprehension that) {
        super.visit(that);
        boolean cs = enterCapturingScope();
        that.getForComprehensionClause().visit(this);
        exitCapturingScope(cs);
    }
    @Override public void visit(Tree.ForComprehensionClause that) {
        super.visit(that);
        final SpecifierExpression specifier = that.getForIterator().getSpecifierExpression();
        if (specifier != null) {
            
            final Expression expr = specifier.getExpression();
            final Term term = expr.getTerm();
            if (term instanceof Tree.Primary) {
                capture((Tree.Primary)term, true);
            }
        }   
        that.getComprehensionClause().visit(this);
    }
    @Override public void visit(Tree.IfComprehensionClause that) {
        super.visit(that);
        //that.getCondition().visit(this);
        that.getComprehensionClause().visit(this);
    }
    @Override public void visit(Tree.ExpressionComprehensionClause that) {
        super.visit(that);
        visitReference(that.getExpression());
    }

    @Override
    public void visit(SpecifierStatement that) {
        boolean cs = inCapturingScope;
        // refining specifiers do capture, as opposed to regular constructor specifiers
        if(that.getRefinement())
            enterCapturingScope();
        super.visit(that);
        if(that.getRefinement())
            exitCapturingScope(cs);
    }
}
