package com.slimgears.util.repository.expressions.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import com.slimgears.util.autovalue.annotations.BuilderPrototype;
import com.slimgears.util.autovalue.annotations.PropertyMeta;
import com.slimgears.util.repository.expressions.CollectionExpression;
import com.slimgears.util.repository.expressions.ObjectExpression;
import com.slimgears.util.repository.expressions.PropertyExpression;

import java.util.Collection;

@AutoValue
public abstract class CollectionPropertyExpression<S, T, B extends BuilderPrototype<T, B>, E> implements PropertyExpression<S, T, B, Collection<E>>, CollectionExpression<S, E> {
    @JsonCreator
    public static <S, T, B extends BuilderPrototype<T, B>, E> CollectionPropertyExpression<S, T, B, E> create(
            @JsonProperty("type") Type type,
            @JsonProperty("target") ObjectExpression<S, T> target,
            @JsonProperty("property") PropertyMeta<T, B, ? extends Collection<E>> property) {
        return new AutoValue_CollectionPropertyExpression<>(type, target, property);
    }
}