package com.slimgears.rxrepo.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.slimgears.rxrepo.encoding.MetaObjectResolver;
import com.slimgears.rxrepo.encoding.codecs.MetaClassCodec;
import com.slimgears.rxrepo.expressions.ObjectExpression;
import com.slimgears.rxrepo.expressions.PropertyExpression;
import com.slimgears.rxrepo.mongodb.adapter.MongoFieldMapper;
import com.slimgears.rxrepo.mongodb.adapter.StandardCodecs;
import com.slimgears.rxrepo.query.provider.AbstractEntityQueryProviderAdapter;
import com.slimgears.rxrepo.query.provider.EntityQueryProvider;
import com.slimgears.rxrepo.query.provider.QueryInfo;
import com.slimgears.util.autovalue.annotations.HasMetaClassWithKey;
import com.slimgears.util.autovalue.annotations.MetaClassWithKey;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

import java.util.concurrent.atomic.AtomicBoolean;

public class MongoQueryProvider extends AbstractEntityQueryProviderAdapter {
    private final MongoClient client;
    private final MongoDatabase database;
    private final Scheduler scheduler = Schedulers.io();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    MongoQueryProvider(String connectionString, String dbName) {
        MetaObjectResolver objectResolver = new ObjectResolver();
        this.client = MetaClassCodec.withResolver(
                objectResolver,
                () -> MongoClients.create(MongoClientSettings
                        .builder()
                        .applyConnectionString(new ConnectionString(connectionString))
                        .codecRegistry(StandardCodecs.registry())
                        .build()));
        this.database = client.getDatabase(dbName);
    }

    @Override
    public void close() {
        isClosed.set(true);
        client.close();
    }

    @Override
    protected <K, S extends HasMetaClassWithKey<K, S>> EntityQueryProvider<K, S> createProvider(MetaClassWithKey<K, S> metaClass) {
        return new MongoEntityQueryProvider<>(metaClass, database, MongoFieldMapper.instance);
    }

    @Override
    protected Scheduler scheduler() {
        return scheduler;
    }

    @Override
    protected Completable dropAllProviders() {
        return Completable.fromPublisher(database.drop())
                .subscribeOn(scheduler);
    }

    private class ObjectResolver implements MetaObjectResolver {
        @Override
        public <K, S extends HasMetaClassWithKey<K, S>> Maybe<S> resolve(MetaClassWithKey<K, S> metaClass, K key) {
            if (isClosed.get()) {
                return Maybe.empty();
            }
            return entities(metaClass)
                    .query(QueryInfo.<K, S, S>builder()
                            .metaClass(metaClass)
                            .predicate(PropertyExpression.ofObject(ObjectExpression.arg(metaClass.asType()), metaClass.keyProperty()).eq(key))
                            .limit(1L)
                            .build())
                    .firstElement();
        }
    }
}
