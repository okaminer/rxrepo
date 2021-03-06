package com.slimgears.rxrepo.expressions.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.slimgears.rxrepo.expressions.ObjectExpression;
import com.slimgears.rxrepo.expressions.UnaryOperationExpression;

@AutoValue
public abstract class ObjectUnaryOperationExpression<S, T, V>
    extends AbstractUnaryOperationExpression<S, T, V>
    implements ObjectExpression<S, V> {
    @JsonCreator
    public static <S, T, V> ObjectUnaryOperationExpression<S, T, V> create(
            @JsonProperty("type") Type type,
            @JsonProperty("operand") ObjectExpression<S, T> operand) {
        return new AutoValue_ObjectUnaryOperationExpression<>(type, operand);
    }
}
