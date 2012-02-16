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

package com.redhat.ceylon.compiler.java.codegen;

import static com.sun.tools.javac.code.Flags.FINAL;

import com.redhat.ceylon.compiler.java.util.Util;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AttributeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CaseClause;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CaseItem;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CatchClause;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ElseClause;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Expression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.FinallyClause;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ForIterator;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.IsCase;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.KeyValueIterator;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.MatchCase;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SatisfiesCase;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SwitchCaseList;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SwitchClause;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SwitchStatement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Throw;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.TryCatchStatement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.TryClause;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ValueIterator;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Variable;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBinary;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCCatch;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

/**
 * This transformer deals with statements only
 */
public class StatementTransformer extends AbstractTransformer {

    // Used to hold the name of the variable associated with the fail-block if the innermost for-loop
    // Is null if we're currently in a while-loop or not in any loop at all
    private Name currentForFailVariable = null;
    
    public static StatementTransformer getInstance(Context context) {
        StatementTransformer trans = context.get(StatementTransformer.class);
        if (trans == null) {
            trans = new StatementTransformer(context);
            context.put(StatementTransformer.class, trans);
        }
        return trans;
    }

    private StatementTransformer(Context context) {
        super(context);
    }

    public JCBlock transform(Tree.Block block) {
        return block == null ? null : at(block).Block(0, transformStmts(block.getStatements()));
    }

    @SuppressWarnings("unchecked")
    List<JCStatement> transformStmts(java.util.List<Tree.Statement> list) {
        CeylonVisitor v = new CeylonVisitor(gen());

        for (Tree.Statement stmt : list)
            stmt.visit(v);

        return (List<JCStatement>) v.getResult().toList();
    }

    List<JCStatement> transform(Tree.IfStatement stmt) {
        Tree.Block thenPart = stmt.getIfClause().getBlock();
        Tree.Block elsePart = stmt.getElseClause() != null ? stmt.getElseClause().getBlock() : null;
        return transformCondition(stmt.getIfClause().getCondition(), JCTree.IF, thenPart, elsePart);
    }

    List<JCStatement> transform(Tree.WhileStatement stmt) {
        Name tempForFailVariable = currentForFailVariable;
        currentForFailVariable = null;
        
        Tree.Block thenPart = stmt.getWhileClause().getBlock();
        List<JCStatement> res = transformCondition(stmt.getWhileClause().getCondition(), JCTree.WHILELOOP, thenPart, null);
        
        currentForFailVariable = tempForFailVariable;
        
        return res;
    }

    private List<JCStatement> transformCondition(Tree.Condition cond, int tag, Tree.Block thenPart, Tree.Block elsePart) {
        JCExpression test;
        JCVariableDecl decl = null;
        JCBlock thenBlock = null;
        JCBlock elseBlock = null;
        if ((cond instanceof Tree.IsCondition) || (cond instanceof Tree.NonemptyCondition) || (cond instanceof Tree.ExistsCondition)) {
            String name;
            ProducedType toType;
            Expression specifierExpr;
            if (cond instanceof Tree.IsCondition) {
                Tree.IsCondition isdecl = (Tree.IsCondition) cond;
                name = isdecl.getVariable().getIdentifier().getText();
                toType = isdecl.getType().getTypeModel();
                specifierExpr = isdecl.getVariable().getSpecifierExpression().getExpression();
            } else if (cond instanceof Tree.NonemptyCondition) {
                Tree.NonemptyCondition nonempty = (Tree.NonemptyCondition) cond;
                name = nonempty.getVariable().getIdentifier().getText();
                toType = nonempty.getVariable().getType().getTypeModel();
                specifierExpr = nonempty.getVariable().getSpecifierExpression().getExpression();
            } else {
                Tree.ExistsCondition exists = (Tree.ExistsCondition) cond;
                name = exists.getVariable().getIdentifier().getText();
                toType = simplifyType(exists.getVariable().getType().getTypeModel());
                specifierExpr = exists.getVariable().getSpecifierExpression().getExpression();
            }
            
            // no need to cast for erasure here
            JCExpression expr = expressionGen().transformExpression(specifierExpr);
            
            // IsCondition with Nothing as ProducedType transformed to " == null" 
            at(cond);
            if (cond instanceof Tree.IsCondition && isNothing(toType)) {
                test = make().Binary(JCTree.EQ, expr, makeNull());
            } else {
                JCExpression toTypeExpr = makeJavaType(toType);
    
                String tmpVarName = aliasName(name);
                Name origVarName = names().fromString(name);
                Name substVarName = names().fromString(aliasName(name));
    
                ProducedType tmpVarType = specifierExpr.getTypeModel();
                JCExpression tmpVarTypeExpr;
                // Want raw type for instanceof since it can't be used with generic types
                JCExpression rawToTypeExpr = makeJavaType(toType, NO_PRIMITIVES | WANT_RAW_TYPE);
    
                // Substitute variable with the correct type to use in the rest of the code block
                JCExpression tmpVarExpr = makeUnquotedIdent(tmpVarName);
                if (cond instanceof Tree.ExistsCondition) {
                    tmpVarExpr = unboxType(tmpVarExpr, toType);
                    tmpVarTypeExpr = makeJavaType(tmpVarType);
                } else if(cond instanceof Tree.IsCondition){
                    tmpVarExpr = unboxType(at(cond).TypeCast(rawToTypeExpr, tmpVarExpr), toType);
                    tmpVarTypeExpr = make().Type(syms().objectType);
                } else {
                    tmpVarExpr = at(cond).TypeCast(toTypeExpr, tmpVarExpr);
                    tmpVarTypeExpr = makeJavaType(tmpVarType);
                }
                
                // Temporary variable holding the result of the expression/variable to test
                decl = makeVar(tmpVarName, tmpVarTypeExpr, null);
    
                // The variable holding the result for the code inside the code block
                JCVariableDecl decl2 = at(cond).VarDef(make().Modifiers(FINAL), substVarName, toTypeExpr, tmpVarExpr);
                
                // Prepare for variable substitution in the following code block
                String prevSubst = addVariableSubst(origVarName.toString(), substVarName.toString());
                
                thenBlock = transform(thenPart);
                List<JCStatement> stats = List.<JCStatement> of(decl2);
                stats = stats.appendList(thenBlock.getStatements());
                thenBlock = at(cond).Block(0, stats);
                
                // Deactivate the above variable substitution
                removeVariableSubst(origVarName.toString(), prevSubst);
                
                at(cond);
                // Assign the expression to test to the temporary variable
                JCExpression testExpr = make().Assign(makeUnquotedIdent(tmpVarName), expr);
                // Use the assignment in the following condition
                if (cond instanceof Tree.ExistsCondition) {
                    test = make().Binary(JCTree.NE, testExpr, makeNull());                
                } else {
                    // nonempty and is
                    test = makeTypeTest(testExpr, expr, toType);
                }
            }
        } else if (cond instanceof Tree.BooleanCondition) {
            Tree.BooleanCondition booleanCondition = (Tree.BooleanCondition) cond;
            // booleans can't be erased
            test = expressionGen().transformExpression(booleanCondition.getExpression(), 
                    BoxingStrategy.UNBOXED, null);
        } else {
            throw new RuntimeException("Not implemented: " + cond.getNodeType());
        }
        
        at(cond);
        // Convert the code blocks (if not already done so above)
        if (thenPart != null && thenBlock == null) {
            thenBlock = transform(thenPart);
        }
        if (elsePart != null && elseBlock == null) {
            elseBlock = transform(elsePart);
        }
        
        JCStatement cond1;
        switch (tag) {
        case JCTree.IF:
            cond1 = make().If(test, thenBlock, elseBlock);
            break;
        case JCTree.WHILELOOP:
            cond1 = make().WhileLoop(makeLetExpr(make().TypeIdent(TypeTags.BOOLEAN), test), thenBlock);
            break;
        default:
            throw new RuntimeException();
        }
        
        if (decl != null) {
            return List.<JCStatement> of(decl, cond1);
        } else {
            return List.<JCStatement> of(cond1);
        }
    }

	List<JCStatement> transform(Tree.ForStatement stmt) {
        Name tempForFailVariable = currentForFailVariable;
        
        at(stmt);
        List<JCStatement> outer = List.<JCStatement> nil();
        if (stmt.getElseClause() != null) {
            // boolean $doforelse$X = true;
            JCVariableDecl failtest_decl = make().VarDef(make().Modifiers(0), names().fromString(aliasName("doforelse")), make().TypeIdent(TypeTags.BOOLEAN), make().Literal(TypeTags.BOOLEAN, 1));
            outer = outer.append(failtest_decl);
            
            currentForFailVariable = failtest_decl.getName();
        } else {
            currentForFailVariable = null;
        }

        // java.lang.Object $elem$X;
        String elem_name = aliasName("elem");
        JCVariableDecl elem_decl = make().VarDef(make().Modifiers(0), names().fromString(elem_name), make().Type(syms().objectType), null);
        outer = outer.append(elem_decl);
        
        ForIterator iterDecl = stmt.getForClause().getForIterator();
        Variable variable;
        Variable variable2;
        if (iterDecl instanceof ValueIterator) {
            variable = ((ValueIterator) iterDecl).getVariable();
            variable2 = null;
        } else if (iterDecl instanceof KeyValueIterator) {
            variable = ((KeyValueIterator) iterDecl).getKeyVariable();
            variable2 = ((KeyValueIterator) iterDecl).getValueVariable();
        } else {
            throw new RuntimeException("Unknown ForIterator");
        }
        
        String loop_var_name = variable.getIdentifier().getText();
        Expression specifierExpression = iterDecl.getSpecifierExpression().getExpression();
        ProducedType sequence_element_type;
        if(variable2 == null)
            sequence_element_type = variable.getType().getTypeModel();
        else{
            // Entry<V1,V2>
            sequence_element_type = typeFact().getEntryType(variable.getType().getTypeModel(), 
                    variable2.getType().getTypeModel());
        }
        ProducedType iter_type = typeFact().getIteratorType(sequence_element_type);
        JCExpression iter_type_expr = makeJavaType(iter_type, CeylonTransformer.TYPE_ARGUMENT);
        JCExpression cast_elem = at(stmt).TypeCast(makeJavaType(sequence_element_type, CeylonTransformer.NO_PRIMITIVES), makeUnquotedIdent(elem_name));
        List<JCAnnotation> annots = makeJavaTypeAnnotations(variable.getDeclarationModel());

        // ceylon.language.Iterator<T> $V$iter$X = ITERABLE.getIterator();
        // We don't need to unerase here as anything remotely a sequence will be erased to Iterable, which has getIterator()
        JCExpression containment = expressionGen().transformExpression(specifierExpression, BoxingStrategy.BOXED, null);
        JCVariableDecl iter_decl = at(stmt).VarDef(make().Modifiers(0), names().fromString(aliasName(loop_var_name + "$iter")), iter_type_expr, at(stmt).Apply(null, makeSelect(containment, "getIterator"), List.<JCExpression> nil()));
        String iter_id = iter_decl.getName().toString();
        
        // final U n = $elem$X;
        // or
        // final U n = $elem$X.getKey();
        JCExpression loop_var_init;
        ProducedType loop_var_type;
        if (variable2 == null) {
            loop_var_type = sequence_element_type;
            loop_var_init = cast_elem;
        } else {
            loop_var_type = actualType(variable);
            loop_var_init = at(stmt).Apply(null, makeSelect(cast_elem, Util.getGetterName("key")), List.<JCExpression> nil());
        }
        JCVariableDecl item_decl = at(stmt).VarDef(make().Modifiers(FINAL, annots), names().fromString(loop_var_name), makeJavaType(loop_var_type), unboxType(loop_var_init, loop_var_type));
        List<JCStatement> for_loop = List.<JCStatement> of(item_decl);

        if (variable2 != null) {
            // final V n = $elem$X.getElement();
            ProducedType item_type2 = actualType(variable2);
            JCExpression item_type_expr2 = makeJavaType(item_type2);
            JCExpression loop_var_init2 = at(stmt).Apply(null, makeSelect(cast_elem, Util.getGetterName("item")), List.<JCExpression> nil());
            String loop_var_name2 = variable2.getIdentifier().getText();
            JCVariableDecl item_decl2 = at(stmt).VarDef(make().Modifiers(FINAL, annots), names().fromString(loop_var_name2), item_type_expr2, unboxType(loop_var_init2, item_type2));
            for_loop = for_loop.append(item_decl2);
        }

        // The user-supplied contents of the loop
        for_loop = for_loop.appendList(transformStmts(stmt.getForClause().getBlock().getStatements()));
        
        // $elem$X = $V$iter$X.next()
        JCExpression iter_elem = make().Apply(null, makeSelect(iter_id, "next"), List.<JCExpression> nil());
        JCExpression elem_assign = make().Assign(makeUnquotedIdent(elem_name), iter_elem);
        // !(($elem$X = $V$iter$X.next()) instanceof Finished)
        JCExpression instof = make().TypeTest(elem_assign, make().Type(syms().ceylonFinishedType));
        JCExpression cond = make().Unary(JCTree.NOT, instof);

        // No step necessary
        List<JCExpressionStatement> step = List.<JCExpressionStatement>nil();
        
        // for (.ceylon.language.Iterator<T> $V$iter$X = ITERABLE.iterator(); !(($elem$X = $V$iter$X.next()) instanceof Finished); ) {
        outer = outer.append(at(stmt).ForLoop(
            List.<JCStatement>of(iter_decl), 
	        cond, 
	        step, 
	        at(stmt).Block(0, for_loop)));

        if (stmt.getElseClause() != null) {
            // The user-supplied contents of fail block
            List<JCStatement> failblock = transformStmts(stmt.getElseClause().getBlock().getStatements());
            
            // if ($doforelse$X) ...
            JCIdent failtest_id = at(stmt).Ident(currentForFailVariable);
            outer = outer.append(at(stmt).If(failtest_id, at(stmt).Block(0, failblock), null));
        }
        currentForFailVariable = tempForFailVariable;

        return outer;
    }

    // FIXME There is a similar implementation in ClassGen!
    public JCStatement transform(AttributeDeclaration decl) {
        Name atrrName = names().fromString(decl.getIdentifier().getText());
        ProducedType t = actualType(decl);
        
        JCExpression initialValue = null;
        if (decl.getSpecifierOrInitializerExpression() != null) {
            initialValue = expressionGen().transformExpression(decl.getSpecifierOrInitializerExpression().getExpression(), 
                    Util.getBoxingStrategy(decl.getDeclarationModel()), 
                    decl.getDeclarationModel().getType());
        }

        JCExpression type = makeJavaType(t);
        List<JCAnnotation> annots = makeJavaTypeAnnotations(decl.getDeclarationModel());

        int modifiers = transformLocalFieldDeclFlags(decl);
        return at(decl).VarDef(at(decl).Modifiers(modifiers, annots), atrrName, type, initialValue);
    }
    
    List<JCStatement> transform(Tree.Break stmt) {
        // break;
        JCStatement brk = at(stmt).Break(null);
        
        if (currentForFailVariable != null) {
            JCIdent failtest_id = at(stmt).Ident(currentForFailVariable);
            List<JCStatement> list = List.<JCStatement> of(at(stmt).Exec(at(stmt).Assign(failtest_id, make().Literal(TypeTags.BOOLEAN, 0))));
            list = list.append(brk);
            return list;
        } else {
            return List.<JCStatement> of(brk);
        }
    }

    JCStatement transform(Tree.Continue stmt) {
        // continue;
        return at(stmt).Continue(null);
    }

    JCStatement transform(Tree.Return ret) {
        Tree.Expression expr = ret.getExpression();
        JCExpression returnExpr = null;
        at(ret);
        if (expr != null) {
            // we can cast to TypedDeclaration here because return with expressions are only in Method or Value
            TypedDeclaration declaration = (TypedDeclaration)ret.getDeclaration();
            // make sure we use the best declaration for boxing and type
            declaration = nonWideningTypeDecl(declaration);
            returnExpr = expressionGen().transformExpression(expr.getTerm(), 
                    Util.getBoxingStrategy(declaration),
                    declaration.getType());
        }
        return at(ret).Return(returnExpr);
    }

    public JCStatement transform(Throw t) {
        at(t);
        Expression expr = t.getExpression();
        final JCExpression exception;
        if (expr == null) {// bare "throw;" stmt
            exception = make().NewClass(null, null,
                    makeIdent(syms().ceylonExceptionType),
                    List.<JCExpression>of(makeNull(), makeNull()),
                    null);
        } else {
            // we must unerase the exception to Throwable
            ProducedType exceptionType = expr.getTypeModel().getSupertype(t.getUnit().getExceptionDeclaration());
            exception = gen().expressionGen().transformExpression(expr, BoxingStrategy.UNBOXED, exceptionType);
        }
        return make().Throw(exception);
    }
    
    public JCStatement transform(TryCatchStatement t) {
        // TODO Support resources -- try(Usage u = ...) { ...
        TryClause tryClause = t.getTryClause();
        at(tryClause);
        JCBlock tryBlock = transform(tryClause.getBlock());

        final ListBuffer<JCCatch> catches = ListBuffer.<JCCatch>lb();
        for (CatchClause catchClause : t.getCatchClauses()) {
            at(catchClause);
            java.util.List<ProducedType> exceptionTypes;
            Variable variable = catchClause.getCatchVariable().getVariable();
            ProducedType exceptionType = variable.getDeclarationModel().getType();
            if (typeFact().isUnion(exceptionType)) {
                exceptionTypes = exceptionType.getDeclaration().getCaseTypes();
            } else {
                exceptionTypes = List.<ProducedType>of(exceptionType);
            }
            for (ProducedType type : exceptionTypes) {
                // catch blocks for each exception in the union
                JCVariableDecl param = make().VarDef(make().Modifiers(Flags.FINAL), names().fromString(variable.getIdentifier().getText()),
                        makeJavaType(type, CATCH), null);
                catches.add(make().Catch(param, transform(catchClause.getBlock())));
            }
        }

        final JCBlock finallyBlock;
        FinallyClause finallyClause = t.getFinallyClause();
        if (finallyClause != null) {
            at(finallyClause);
            finallyBlock = transform(finallyClause.getBlock());
        } else {
            finallyBlock = null;
        }

        return at(t).Try(tryBlock, catches.toList(), finallyBlock);
    }

    private int transformLocalFieldDeclFlags(Tree.AttributeDeclaration cdecl) {
        int result = 0;

        result |= cdecl.getDeclarationModel().isVariable() ? 0 : FINAL;

        return result;
    }
    
    /**
     * Transforms a Ceylon switch to a Java {@code if/else if} chain.
     * @param stmt The Ceylon switch
     * @return The Java tree
     */
    JCStatement transform(SwitchStatement stmt) {

        SwitchClause switchClause = stmt.getSwitchClause();
        JCExpression selectorExpr = expressionGen().transformExpression(switchClause.getExpression(), BoxingStrategy.UNBOXED, switchClause.getExpression().getTypeModel());
        String selectorAlias = aliasName("sel");
        JCVariableDecl selector = makeVar(selectorAlias, make().Type(syms().objectType), selectorExpr);
        SwitchCaseList caseList = stmt.getSwitchCaseList();

        JCStatement last = null;
        ElseClause elseClause = caseList.getElseClause();
        if (elseClause != null) {
            last = transform(elseClause.getBlock());
        } else {
            // To avoid possible javac warnings about uninitialized vars we
            // need to have an 'else' clause, even if the ceylon code doesn't
            // require one. 
            // This exception could be thrown for example if an enumerated 
            // type is recompiled after having a subclass added, but the 
            // switch is not recompiled.
            last = make().Throw(
                        make().NewClass(null, List.<JCExpression>nil(), 
                                makeIdent(syms().ceylonEnumeratedTypeErrorType), 
                                List.<JCExpression>of(make().Literal(
                                        "Supposedly exhaustive switch was not exhaustive")), null));
        }

        java.util.List<CaseClause> caseClauses = caseList.getCaseClauses();
        for (int ii = caseClauses.size() - 1; ii >= 0; ii--) {// reverse order
            CaseClause caseClause = caseClauses.get(ii);
            CaseItem caseItem = caseClause.getCaseItem();
            if (caseItem instanceof IsCase) {
                last = transformCaseIs(selectorAlias, caseClause, (IsCase)caseItem, last);
            } else if (caseItem instanceof SatisfiesCase) {
                // TODO Support for 'case (satisfies ...)' is not implemented yet
                return make().Exec(makeErroneous(caseItem, "switch/satisfies not implemented yet"));
            } else if (caseItem instanceof MatchCase) {
                last = transformCaseMatch(selectorAlias, caseClause, (MatchCase)caseItem, last);
            } else {
                return make().Exec(makeErroneous(caseItem, "unknown switch case clause: "+caseItem));
            }
        }
        return at(stmt).Block(0, List.of(selector, last));
    }

    private JCStatement transformCaseMatch(String selectorAlias, CaseClause caseClause, MatchCase matchCase, JCStatement last) {
        at(matchCase);
        
        JCExpression tests = null;
        java.util.List<Tree.Expression> expressions = matchCase.getExpressionList().getExpressions();
        for(Tree.Expression expr : expressions){
            JCExpression transformedExpression = expressionGen().transformExpression(expr);
            JCBinary test = make().Binary(JCTree.EQ, makeUnquotedIdent(selectorAlias), transformedExpression);
            if(tests == null)
                tests = test;
            else
                tests = make().Binary(JCTree.OR, tests, test);
        }
        return at(caseClause).If(tests, transform(caseClause.getBlock()), last);
    }

    /**
     * Transform a "case(is ...)"
     * @param selectorAlias
     * @param caseClause
     * @param isCase
     * @param last
     * @return
     */
    private JCStatement transformCaseIs(String selectorAlias,
            CaseClause caseClause, IsCase isCase, JCStatement last) {
        at(isCase);
        ProducedType type = isCase.getType().getTypeModel();
        JCExpression cond = makeTypeTest(makeUnquotedIdent(selectorAlias), type);
        
        JCExpression toTypeExpr = makeJavaType(type);
        String name = isCase.getVariable().getIdentifier().getText();

        String tmpVarName = selectorAlias;
        Name origVarName = names().fromString(name);
        Name substVarName = names().fromString(aliasName(name));

        // Want raw type for instanceof since it can't be used with generic types
        JCExpression rawToTypeExpr = makeJavaType(type, NO_PRIMITIVES | WANT_RAW_TYPE);

        // Substitute variable with the correct type to use in the rest of the code block
        JCExpression tmpVarExpr = makeUnquotedIdent(tmpVarName);

        tmpVarExpr = unboxType(at(isCase).TypeCast(rawToTypeExpr, tmpVarExpr), type);
        
        // The variable holding the result for the code inside the code block
        JCVariableDecl decl2 = at(isCase).VarDef(make().Modifiers(FINAL), substVarName, toTypeExpr, tmpVarExpr);

        // Prepare for variable substitution in the following code block
        String prevSubst = addVariableSubst(origVarName.toString(), substVarName.toString());

        JCBlock block = transform(caseClause.getBlock());
        List<JCStatement> stats = List.<JCStatement> of(decl2);
        stats = stats.appendList(block.getStatements());
        block = at(isCase).Block(0, stats);

        // Deactivate the above variable substitution
        removeVariableSubst(origVarName.toString(), prevSubst);

        last = make().If(cond, block, last);
        return last;
    }
}
