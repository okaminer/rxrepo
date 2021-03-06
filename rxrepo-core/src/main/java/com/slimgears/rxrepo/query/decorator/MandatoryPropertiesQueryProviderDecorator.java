package com.slimgears.rxrepo.query.decorator;

import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.slimgears.rxrepo.expressions.PropertyExpression;
import com.slimgears.rxrepo.query.Notification;
import com.slimgears.rxrepo.query.provider.QueryInfo;
import com.slimgears.rxrepo.query.provider.QueryProvider;
import com.slimgears.rxrepo.util.PropertyExpressions;
import com.slimgears.rxrepo.util.PropertyMetas;
import io.reactivex.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.slimgears.util.generic.LazyString.lazy;

public class MandatoryPropertiesQueryProviderDecorator extends AbstractQueryProviderDecorator {
    private final static Logger log = LoggerFactory.getLogger(MandatoryPropertiesQueryProviderDecorator.class);

    private MandatoryPropertiesQueryProviderDecorator(QueryProvider underlyingProvider) {
        super(underlyingProvider);
    }

    public static QueryProvider.Decorator create() {
        return MandatoryPropertiesQueryProviderDecorator::new;
    }

    @Override
    public <K, S, T> Observable<T> query(QueryInfo<K, S, T> query) {
        return query.properties().isEmpty()
                ? super.query(query)
                : super.query(query.toBuilder()
                        .apply(includeProperties(query.properties(), query.objectType()))
                        .build());
    }

    @Override
    public <K, S, T> Observable<Notification<T>> liveQuery(QueryInfo<K, S, T> query) {
        return query.properties().isEmpty()
                ? super.liveQuery(query)
                : super.liveQuery(query.toBuilder()
                        .apply(includeProperties(query.properties(), query.objectType()))
                        .build());
    }

    private static <K, S, T> Consumer<QueryInfo.Builder<K, S, T>> includeProperties(Collection<PropertyExpression<T, ?, ?>> properties, TypeToken<T> typeToken) {
        return builder -> {
            Stream<PropertyExpression<T, ?, ?>> includedProperties = properties.stream()
                    .flatMap(PropertyExpressions::mandatoryProperties)
                    .distinct();

            if (PropertyMetas.hasMetaClass(typeToken)) {
                includedProperties = Stream.concat(includedProperties, PropertyExpressions.mandatoryProperties(typeToken));
            }

            Collection<PropertyExpression<T, ?, ?>> props = includedProperties.collect(Collectors.toCollection(Sets::newLinkedHashSet));
            log.trace("Requested properties: [{}], Final properties: [{}]",
                    lazy(() -> properties.stream().map(PropertyExpressions::pathOf).collect(Collectors.joining(", "))),
                    lazy(() -> props.stream().map(PropertyExpressions::pathOf).collect(Collectors.joining(", "))));

            builder.propertiesAddAll(props);
        };
    }
}
