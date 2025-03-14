/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.resolve;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.sonar.java.ast.api.JavaKeyword;
import org.sonar.java.model.AbstractTypedTree;
import org.sonar.java.model.JavaTree;
import org.sonar.plugins.java.api.tree.AnnotationTree;
import org.sonar.plugins.java.api.tree.ArrayAccessExpressionTree;
import org.sonar.plugins.java.api.tree.ArrayTypeTree;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.BinaryExpressionTree;
import org.sonar.plugins.java.api.tree.BreakStatementTree;
import org.sonar.plugins.java.api.tree.ConditionalExpressionTree;
import org.sonar.plugins.java.api.tree.ContinueStatementTree;
import org.sonar.plugins.java.api.tree.EnumConstantTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.ImportTree;
import org.sonar.plugins.java.api.tree.InstanceOfTree;
import org.sonar.plugins.java.api.tree.LabeledStatementTree;
import org.sonar.plugins.java.api.tree.LambdaExpressionTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.NewArrayTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ParameterizedTypeTree;
import org.sonar.plugins.java.api.tree.ParenthesizedTree;
import org.sonar.plugins.java.api.tree.PrimitiveTypeTree;
import org.sonar.plugins.java.api.tree.ThrowStatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeCastTree;
import org.sonar.plugins.java.api.tree.UnaryExpressionTree;
import org.sonar.plugins.java.api.tree.UnionTypeTree;
import org.sonar.plugins.java.api.tree.VariableTree;
import org.sonar.plugins.java.api.tree.WildcardTree;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Computes types and references of Identifier and MemberSelectExpression.
 */
public class TypeAndReferenceSolver extends BaseTreeVisitor {

  private final Map<Tree.Kind, Type> typesOfLiterals = Maps.newEnumMap(Tree.Kind.class);

  private final SemanticModel semanticModel;
  private final Symbols symbols;
  private final Resolve resolve;

  private final Map<Tree, Type> types = Maps.newHashMap();

  public TypeAndReferenceSolver(SemanticModel semanticModel, Symbols symbols, Resolve resolve) {
    this.semanticModel = semanticModel;
    this.symbols = symbols;
    this.resolve = resolve;
    typesOfLiterals.put(Tree.Kind.BOOLEAN_LITERAL, symbols.booleanType);
    typesOfLiterals.put(Tree.Kind.NULL_LITERAL, symbols.nullType);
    typesOfLiterals.put(Tree.Kind.CHAR_LITERAL, symbols.charType);
    typesOfLiterals.put(Tree.Kind.STRING_LITERAL, symbols.stringType);
    typesOfLiterals.put(Tree.Kind.FLOAT_LITERAL, symbols.floatType);
    typesOfLiterals.put(Tree.Kind.DOUBLE_LITERAL, symbols.doubleType);
    typesOfLiterals.put(Tree.Kind.LONG_LITERAL, symbols.longType);
    typesOfLiterals.put(Tree.Kind.INT_LITERAL, symbols.intType);
  }

  @Override
  public void visitImport(ImportTree tree) {
    //Noop, imports are not expression
  }

  @Override
  public void visitLabeledStatement(LabeledStatementTree tree) {
    //Ignore label (dedicated visitor)
    scan(tree.statement());
  }

  @Override
  public void visitBreakStatement(BreakStatementTree tree) {
    //Ignore break (dedicated visitor)
  }

  @Override
  public void visitContinueStatement(ContinueStatementTree tree) {
    //Ignore continue (dedicated visitor)
  }

  @Override
  public void visitExpressionStatement(ExpressionStatementTree tree) {
    super.visitExpressionStatement(tree);
    // TODO(Godin): strictly speaking statement can't have type
    registerType(tree, getType(tree.expression()));
  }

  @Override
  public void visitMethodInvocation(MethodInvocationTree tree) {
    Tree methodSelect = tree.methodSelect();
    Resolve.Env env = semanticModel.getEnv(tree);
    IdentifierTree identifier;
    Type type;
    String name;
    if (methodSelect.is(Tree.Kind.MEMBER_SELECT)) {
      MemberSelectExpressionTree mset = (MemberSelectExpressionTree) methodSelect;
      resolveAs(mset.expression(), Symbol.TYP | Symbol.VAR);
      type = getType(mset.expression());
      identifier = mset.identifier();
    } else if (methodSelect.is(Tree.Kind.IDENTIFIER)) {
      type = env.enclosingClass.type;
      identifier = (IdentifierTree) methodSelect;
    } else {
      throw new IllegalStateException("Method select in method invocation is not of the expected type " + methodSelect);
    }
    if (tree.typeArguments() != null) {
      resolveAs((List<? extends Tree>) tree.typeArguments(), Symbol.VAR);
    }
    resolveAs(tree.arguments(), Symbol.VAR);
    name = identifier.name();
    if (type == null) {
      type = symbols.unknownType;
    }
    Symbol symbol = resolve.findMethod(env, type.symbol, name, ImmutableList.<Type>of());
    associateReference(identifier, symbol);
    type = getTypeOfSymbol(symbol);
    //Register return type for method invocation.
    //TODO register method type for method select ?
    if (type == null || symbol.kind >= Symbol.ERRONEOUS) {
      registerType(tree, symbols.unknownType);
    } else {
      registerType(tree, ((Symbol.MethodSymbol) symbol).getReturnType().getType());
    }
  }

  public Symbol resolveAs(@Nullable Tree tree, int kind) {
    if (tree == null) {
      return null;
    }
    if (tree.is(Tree.Kind.IDENTIFIER)) {
      IdentifierTree identifierTree = (IdentifierTree) tree;
      Symbol ident = resolve.findIdent(semanticModel.getEnv(tree), identifierTree.name(), kind);
      associateReference(identifierTree, ident);
      registerType(identifierTree, getTypeOfSymbol(ident));
      return ident;
    } else if (tree.is(Tree.Kind.MEMBER_SELECT)) {
      MemberSelectExpressionTree mse = (MemberSelectExpressionTree) tree;
      if (JavaKeyword.CLASS.getValue().equals(mse.identifier().name())) {
        // member select ending with .class
        registerType(tree, symbols.classType);
        return null;
      }
      int expressionKind = Symbol.TYP | Symbol.VAR;
      if ((kind & (Symbol.PCK | Symbol.TYP)) != 0) {
        expressionKind = Symbol.PCK | Symbol.TYP | kind;
      }
      Symbol site = resolveAs(mse.expression(), expressionKind);
      if (site.kind == Symbol.VAR) {
        site = site.type.symbol;
      }
      Symbol ident = symbols.unknownSymbol;
      if (site.kind == Symbol.TYP) {
        ident = resolve.findIdentInType(semanticModel.getEnv(tree), (Symbol.TypeSymbol) site, mse.identifier().name(), kind);
      } else if (site.kind == Symbol.PCK) {
        //package
        ident = resolve.findIdentInPackage(site, mse.identifier().name(), kind);
      }
      associateReference(mse.identifier(), ident);
      registerType(mse.identifier(), getTypeOfSymbol(ident));
      registerType(tree, getTypeOfSymbol(ident));
      return ident;
    }
    tree.accept(this);
    Type type = getType(tree);
    if (tree.is(Tree.Kind.INFERED_TYPE)) {
      return null;
    }
    if (type == null) {
      throw new IllegalStateException("Type not resolved " + tree);
    }
    return type.symbol;
  }

  public void resolveAs(List<? extends Tree> trees, int kind) {
    for (Tree tree : trees) {
      resolveAs(tree, kind);
    }
  }

  @Override
  public void visitInstanceOf(InstanceOfTree tree) {
    resolveAs(tree.expression(), Symbol.VAR);
    resolveAs(tree.type(), Symbol.TYP);
    registerType(tree, symbols.booleanType);
  }

  @Override
  public void visitParameterizedType(ParameterizedTypeTree tree) {
    resolveAs(tree.type(), Symbol.TYP);
    resolveAs(tree.typeArguments(), Symbol.TYP);
    //FIXME(benzonico) approximation of generic type to its raw type
    registerType(tree, getType(tree.type()));
  }

  @Override
  public void visitWildcard(WildcardTree tree) {
    if (tree.bound() == null) {
      registerType(tree, symbols.unknownType);
    } else {
      resolveAs(tree.bound(), Symbol.TYP);
      registerType(tree, getType(tree.bound()));
    }
  }

  @Override
  public void visitConditionalExpression(ConditionalExpressionTree tree) {
    resolveAs(tree.condition(), Symbol.VAR);
    resolveAs(tree.trueExpression(), Symbol.VAR);
    resolveAs(tree.falseExpression(), Symbol.VAR);
    registerType(tree, symbols.unknownType);
  }

  @Override
  public void visitThrowStatement(ThrowStatementTree tree) {
    resolveAs(tree.expression(), Symbol.VAR);
  }

  @Override
  public void visitLambdaExpression(LambdaExpressionTree tree) {
    //TODO resolve variables
    super.visitLambdaExpression(tree);
    registerType(tree, symbols.unknownType);
  }

  @Override
  public void visitNewArray(NewArrayTree tree) {
    resolveAs(tree.type(), Symbol.TYP);
    resolveAs(tree.dimensions(), Symbol.VAR);
    resolveAs(tree.initializers(), Symbol.VAR);
    Type type = getType(tree.type());
    int dimensions = tree.dimensions().size();
    // TODO why?
    type = new Type.ArrayType(type, symbols.arrayClass);
    for (int i = 1; i < dimensions; i++) {
      type = new Type.ArrayType(type, symbols.arrayClass);
    }
    registerType(tree, type);
  }

  @Override
  public void visitParenthesized(ParenthesizedTree tree) {
    resolveAs(tree.expression(), Symbol.VAR);
    registerType(tree, getType(tree.expression()));
  }

  @Override
  public void visitArrayAccessExpression(ArrayAccessExpressionTree tree) {
    resolveAs(tree.expression(), Symbol.VAR);
    resolveAs(tree.index(), Symbol.VAR);
    Type type = getType(tree.expression());
    if (type != null && type.tag == Type.ARRAY) {
      registerType(tree, ((Type.ArrayType) type).elementType);
    } else {
      registerType(tree, symbols.unknownType);
    }
  }

  @Override
  public void visitBinaryExpression(BinaryExpressionTree tree) {
    resolveAs(tree.leftOperand(), Symbol.VAR);
    resolveAs(tree.rightOperand(), Symbol.VAR);
    Resolve.Env env = semanticModel.getEnv(tree);
    Type left = getType(tree.leftOperand());
    Type right = getType(tree.rightOperand());
    // TODO avoid nulls
    if (left == null || right == null) {
      registerType(tree, symbols.unknownType);
      return;
    }
    Symbol symbol = resolve.findMethod(env, tree.operatorToken().text(), ImmutableList.of(left, right));
    if (symbol.kind != Symbol.MTH) {
      // not found
      registerType(tree, symbols.unknownType);
      return;
    }
    registerType(tree, ((Type.MethodType) symbol.type).resultType);
  }

  @Override
  public void visitNewClass(NewClassTree tree) {
    if (tree.enclosingExpression() != null) {
      resolveAs(tree.enclosingExpression(), Symbol.VAR);
    }
    resolveAs(tree.identifier(), Symbol.TYP);
    resolveAs(tree.typeArguments(), Symbol.TYP);
    resolveAs(tree.arguments(), Symbol.VAR);
    if (tree.classBody() != null) {
      //TODO create a new symbol and type for each anonymous class
      scan(tree.classBody());
      registerType(tree, symbols.unknownType);
    } else {
      registerType(tree, getType(tree.identifier()));
    }
  }

  @Override
  public void visitPrimitiveType(PrimitiveTypeTree tree) {
    Type type = resolve.findIdent(semanticModel.getEnv(tree), tree.keyword().text(), Symbol.TYP).type;
    registerType(tree, type);
  }

  @Override
  public void visitVariable(VariableTree tree) {
    scan(tree.modifiers());
    //skip type, it has been resolved in second pass
    if (tree.initializer() != null) {
      resolveAs(tree.initializer(), Symbol.VAR);
    }
  }

  /**
   * Computes type of an assignment expression. Which is always a type of lvalue.
   * For example in case of {@code double d; int i; res = d = i;} type of assignment expression {@code d = i} is double.
   */
  @Override
  public void visitAssignmentExpression(AssignmentExpressionTree tree) {
    resolveAs(tree.variable(), Symbol.VAR);
    resolveAs(tree.expression(), Symbol.VAR);
    Type type = getType(tree.variable());
    registerType(tree, type);
  }

  @Override
  public void visitLiteral(LiteralTree tree) {
    Type type = typesOfLiterals.get(((JavaTree) tree).getKind());
    registerType(tree, type);
  }

  @Override
  public void visitUnaryExpression(UnaryExpressionTree tree) {
    resolveAs(tree.expression(), Symbol.VAR);
    registerType(tree, getType(tree.expression()));
  }

  @Override
  public void visitArrayType(ArrayTypeTree tree) {
    //FIXME(benzonico) respect invariant to set type. Required for cases like : int i[],j[]; Compute array element type only if not previously computed.
    if (getType(tree.type()) == null) {
      resolveAs(tree.type(), Symbol.TYP);
    }
    registerType(tree, new Type.ArrayType(getType(tree.type()), symbols.arrayClass));
  }

  @Override
  public void visitTypeCast(TypeCastTree tree) {
    resolveAs(tree.type(), Symbol.TYP);
    resolveAs(tree.expression(), Symbol.VAR);
    registerType(tree, getType(tree.type()));
  }

  @Override
  public void visitUnionType(UnionTypeTree tree) {
    resolveAs(tree.typeAlternatives(), Symbol.TYP);
    //TODO compute type of union type: lub(alternatives) cf JLS8 14.20
    registerType(tree, symbols.unknownType);
  }

  @Override
  public void visitEnumConstant(EnumConstantTree tree) {
    scan(tree.modifiers());
    NewClassTree newClassTree = (NewClassTree) tree.initializer();
    scan(newClassTree.enclosingExpression());
    // skip identifier
    scan(newClassTree.typeArguments());
    scan(newClassTree.arguments());
    scan(newClassTree.classBody());
  }

  @Override
  public void visitAnnotation(AnnotationTree tree) {
    resolveAs(tree.annotationType(), Symbol.TYP);
    for (ExpressionTree expressionTree : tree.arguments()) {
      resolveAs(expressionTree, Symbol.VAR);
    }
    registerType(tree, getType(tree.annotationType()));
  }

  @Override
  public void visitIdentifier(IdentifierTree tree) {
    if (((AbstractTypedTree) tree).getSymbolType() == null) {
      resolveAs(tree, Symbol.VAR);
    }
  }

  @Override
  public void visitMemberSelectExpression(MemberSelectExpressionTree tree) {
    if (((AbstractTypedTree) tree).getSymbolType() == null) {
      resolveAs(tree, Symbol.VAR);
    }
  }

  @Override
  public void visitOther(Tree tree) {
    registerType(tree, symbols.unknownType);
  }

  private Type getTypeOfSymbol(Symbol symbol) {
    if (symbol.kind < Symbol.ERRONEOUS) {
      return symbol.type;
    } else {
      return symbols.unknownType;
    }
  }

  @VisibleForTesting
  Type getType(Tree tree) {
    return types.get(tree);
  }

  private void registerType(Tree tree, Type type) {
    if (AbstractTypedTree.class.isAssignableFrom(tree.getClass())) {
      ((AbstractTypedTree) tree).setType(type);
    }
    types.put(tree, type);
  }

  private void associateReference(IdentifierTree tree, Symbol symbol) {
    if (symbol.kind < Symbol.ERRONEOUS) {
      semanticModel.associateReference(tree, symbol);
    }
  }

}
