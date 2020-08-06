/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.expression.function;

import org.h2.engine.SessionLocal;
import org.h2.expression.Expression;
import org.h2.expression.ExpressionVisitor;
import org.h2.expression.ValueExpression;
import org.h2.schema.FunctionAlias;
import org.h2.table.ColumnResolver;
import org.h2.table.TableFilter;
import org.h2.value.TypeInfo;
import org.h2.value.Value;
import org.h2.value.ValueNull;
import org.h2.value.ValueResultSet;
import org.h2.value.ValueRow;

/**
 * This class wraps a user-defined function.
 */
public final class JavaFunction extends Expression implements FunctionCall {

    private final FunctionAlias functionAlias;
    private final FunctionAlias.JavaMethod javaMethod;
    private final Expression[] args;

    public JavaFunction(FunctionAlias functionAlias, Expression[] args) {
        this.functionAlias = functionAlias;
        this.javaMethod = functionAlias.findJavaMethod(args);
        this.args = args;
    }

    @Override
    public Value getValue(SessionLocal session) {
        return javaMethod.getValue(session, args, false);
    }

    @Override
    public TypeInfo getType() {
        return javaMethod.getDataType();
    }

    @Override
    public int getValueType() {
        return javaMethod.getDataType().getValueType();
    }

    @Override
    public void mapColumns(ColumnResolver resolver, int level, int state) {
        for (Expression e : args) {
            e.mapColumns(resolver, level, state);
        }
    }

    @Override
    public Expression optimize(SessionLocal session) {
        boolean allConst = isDeterministic();
        for (int i = 0, len = args.length; i < len; i++) {
            Expression e = args[i].optimize(session);
            args[i] = e;
            allConst &= e.isConstant();
        }
        if (allConst) {
            return ValueExpression.get(getValue(session));
        }
        return this;
    }

    @Override
    public void setEvaluatable(TableFilter tableFilter, boolean b) {
        for (Expression e : args) {
            if (e != null) {
                e.setEvaluatable(tableFilter, b);
            }
        }
    }

    @Override
    public StringBuilder getUnenclosedSQL(StringBuilder builder, int sqlFlags) {
        return writeExpressions(functionAlias.getSQL(builder, sqlFlags).append('('), args, sqlFlags).append(')');
    }

    @Override
    public void updateAggregate(SessionLocal session, int stage) {
        for (Expression e : args) {
            if (e != null) {
                e.updateAggregate(session, stage);
            }
        }
    }

    @Override
    public String getName() {
        return functionAlias.getName();
    }

    @Override
    public ValueResultSet getValueForColumnList(SessionLocal session,
            Expression[] argList) {
        Value v = javaMethod.getValue(session, argList, true);
        return v == ValueNull.INSTANCE ? null : (ValueResultSet) v;
    }

    @Override
    public Expression[] getArgs() {
        return args;
    }

    @Override
    public boolean isEverything(ExpressionVisitor visitor) {
        switch (visitor.getType()) {
        case ExpressionVisitor.DETERMINISTIC:
            if (!isDeterministic()) {
                return false;
            }
            // only if all parameters are deterministic as well
            break;
        case ExpressionVisitor.GET_DEPENDENCIES:
            visitor.addDependency(functionAlias);
            break;
        default:
        }
        for (Expression e : args) {
            if (e != null && !e.isEverything(visitor)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getCost() {
        int cost = javaMethod.hasConnectionParam() ? 25 : 5;
        for (Expression e : args) {
            cost += e.getCost();
        }
        return cost;
    }

    @Override
    public boolean isDeterministic() {
        return functionAlias.isDeterministic();
    }

    @Override
    public Expression[] getExpressionColumns(SessionLocal session) {
        switch (getValueType()) {
        case Value.RESULT_SET:
            ValueResultSet rs = getValueForColumnList(session, getArgs());
            return getExpressionColumns(session, rs.getResult());
        case Value.ROW:
            return getExpressionColumns(session, (ValueRow) getValue(session));
        }
        return super.getExpressionColumns(session);
    }

    @Override
    public int getSubexpressionCount() {
        return args.length;
    }

    @Override
    public Expression getSubexpression(int index) {
        return args[index];
    }

}
