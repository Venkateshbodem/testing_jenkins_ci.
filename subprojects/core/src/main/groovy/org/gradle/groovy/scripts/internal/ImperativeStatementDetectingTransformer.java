/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.groovy.scripts.internal;

import com.google.common.collect.ImmutableList;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.classgen.BytecodeExpression;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.gradle.groovy.scripts.Transformer;
import org.gradle.model.dsl.internal.transform.ModelBlockTransformer;

import java.util.List;

public class ImperativeStatementDetectingTransformer extends AbstractScriptTransformer {

    private final List<String> scriptBlockNames;
    private final Transformer buildScriptTransformer;
    private final String id;

    private boolean imperativeStatementDetected;

    public ImperativeStatementDetectingTransformer(String id, Transformer buildScriptTransformer, String classpathClosureName) {
        this.buildScriptTransformer = buildScriptTransformer;
        this.id = id;
        scriptBlockNames = ImmutableList.of(classpathClosureName, PluginsAndBuildscriptTransformer.PLUGINS, ModelBlockTransformer.MODEL);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void register(CompilationUnit compilationUnit) {
        buildScriptTransformer.register(compilationUnit);
        super.register(compilationUnit);
    }

    @Override
    protected int getPhase() {
        return Phases.CANONICALIZATION;
    }

    public boolean isImperativeStatementDetected() {
        return imperativeStatementDetected;
    }

    @Override
    public void call(SourceUnit source) throws CompilationFailedException {
        if (!source.getAST().getMethods().isEmpty()) {
            imperativeStatementDetected = true;
            return;
        }

        BlockStatement statementBlock = source.getAST().getStatementBlock();
        List<Statement> statements = statementBlock.getStatements();
        if (statements.size() == 1 && AstUtils.isReturnNullStatement(statements.get(0))) {
            return;
        }

        ImperativeStatementDetectingVisitor visitor = new ImperativeStatementDetectingVisitor(scriptBlockNames);
        for (int i = 0; i < statements.size() && !visitor.isImperativeStatementDetected(); i++) {
            statements.get(i).visit(visitor);
        }
        imperativeStatementDetected = visitor.isImperativeStatementDetected();
    }

    private static class ImperativeStatementDetectingVisitor implements GroovyCodeVisitor {

        private final List<String> scriptBlockNames;
        private boolean imperativeStatementDetected;

        public ImperativeStatementDetectingVisitor(List<String> scriptBlockNames) {
            this.scriptBlockNames = scriptBlockNames;
        }

        public boolean isImperativeStatementDetected() {
            return imperativeStatementDetected;
        }

        @Override
        public void visitBlockStatement(BlockStatement block) {
            for (Statement statement : block.getStatements()) {
                statement.visit(this);
            }
        }

        @Override
        public void visitForLoop(ForStatement forLoop) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitWhileLoop(WhileStatement loop) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitDoWhileLoop(DoWhileStatement loop) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitIfElse(IfStatement ifElse) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitExpressionStatement(ExpressionStatement statement) {
            statement.getExpression().visit(this);
        }

        @Override
        public void visitReturnStatement(ReturnStatement statement) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitAssertStatement(AssertStatement statement) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitTryCatchFinally(TryCatchStatement finally1) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitSwitch(SwitchStatement statement) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitCaseStatement(CaseStatement statement) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitBreakStatement(BreakStatement statement) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitContinueStatement(ContinueStatement statement) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitThrowStatement(ThrowStatement statement) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitSynchronizedStatement(SynchronizedStatement statement) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitCatchStatement(CatchStatement statement) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitMethodCallExpression(MethodCallExpression call) {
            String methodName = AstUtils.extractConstantMethodName(call);
            if (methodName == null) {
                imperativeStatementDetected = true;
                return;
            }

            ClosureExpression closureExpression = AstUtils.getSingleClosureArg(call);
            if (closureExpression == null || !scriptBlockNames.contains(methodName)) {
                imperativeStatementDetected = true;
            }
        }

        @Override
        public void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitConstructorCallExpression(ConstructorCallExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitTernaryExpression(TernaryExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitShortTernaryExpression(ElvisOperatorExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitBinaryExpression(BinaryExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitPrefixExpression(PrefixExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitPostfixExpression(PostfixExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitBooleanExpression(BooleanExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitClosureExpression(ClosureExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitTupleExpression(TupleExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitMapExpression(MapExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitMapEntryExpression(MapEntryExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitListExpression(ListExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitRangeExpression(RangeExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitPropertyExpression(PropertyExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitAttributeExpression(AttributeExpression attributeExpression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitFieldExpression(FieldExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitMethodPointerExpression(MethodPointerExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitConstantExpression(ConstantExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitClassExpression(ClassExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitVariableExpression(VariableExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitDeclarationExpression(DeclarationExpression expression) {
        }

        @Override
        public void visitGStringExpression(GStringExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitArrayExpression(ArrayExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitSpreadExpression(SpreadExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitSpreadMapExpression(SpreadMapExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitNotExpression(NotExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitUnaryMinusExpression(UnaryMinusExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitUnaryPlusExpression(UnaryPlusExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitCastExpression(CastExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitArgumentlistExpression(ArgumentListExpression expression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitClosureListExpression(ClosureListExpression closureListExpression) {
            imperativeStatementDetected = true;
        }

        @Override
        public void visitBytecodeExpression(BytecodeExpression expression) {
            imperativeStatementDetected = true;
        }
    }
}
