package com.slimgears.rxrepo.orientdb;

import com.google.common.reflect.TypeToken;
import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.index.OIndexException;
import com.orientechnologies.orient.core.index.ORuntimeKeyIndexDefinition;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.serialization.serializer.binary.impl.OLinkSerializer;
import com.slimgears.rxrepo.annotations.Indexable;
import com.slimgears.rxrepo.annotations.Searchable;
import com.slimgears.rxrepo.sql.SchemaProvider;
import com.slimgears.rxrepo.util.PropertyMetas;
import com.slimgears.util.autovalue.annotations.*;
import com.slimgears.util.stream.Streams;
import io.reactivex.Completable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

class OrientDbSchemaProvider implements SchemaProvider {
    private final static Logger log = LoggerFactory.getLogger(OrientDbSchemaProvider.class);
    private final OrientDbSessionProvider dbSessionProvider;

    OrientDbSchemaProvider(OrientDbSessionProvider sessionProvider) {
        this.dbSessionProvider = sessionProvider;
    }

    @Override
    public String databaseName() {
        return dbSessionProvider.withSession(ODatabaseDocument::getName);
    }

    @Override
    public <T> Completable createOrUpdate(MetaClass<T> metaClass) {
        return Completable
                .fromAction(() -> dbSessionProvider.withSession(dbSession -> (OClass)createClass(dbSession, metaClass)));
    }

    @Override
    public <T> String tableName(MetaClass<T> metaClass) {
        return toClassName(metaClass);
    }

    private OClass getOrCreateClass(ODatabaseDocument dbSession, MetaClass<?> metaClass) {
        return Optional.ofNullable(dbSession.getClass(toClassName(metaClass)))
                .orElseGet(() -> createClass(dbSession, metaClass));
    }

    private synchronized OClass createClass(ODatabaseDocument dbSession, MetaClass<?> metaClass) {
        String className = toClassName(metaClass);
        log.debug("Creating class: {}", className);
        OClass oClass = dbSession.createClassIfNotExist(className);

        Streams.fromIterable(metaClass.properties())
                .forEach(p -> {
                    log.trace(">> Adding property {}.{}", className, p.name());
                    try {
                        addProperty(dbSession, oClass, p);
                    } finally {
                        log.trace("<< Adding property {}.{}", className, p.name());
                    }
                });

        if (metaClass instanceof MetaClassWithKey) {
            log.trace("{}: Adding key index", className);
            MetaClassWithKey metaClassWithKey = (MetaClassWithKey) metaClass;

            OType oType = toOType(metaClassWithKey.keyProperty().type());
            if (oType.isLink()) {
                log.trace("{}: Adding reference key index", className);
                dbSession.getMetadata().getIndexManager().createIndex(
                        className + "." + metaClassWithKey.keyProperty().name() + "Index",
                        OClass.INDEX_TYPE.UNIQUE_HASH_INDEX.name(),
                        new ORuntimeKeyIndexDefinition<>(OLinkSerializer.ID),
                        null,
                        null,
                        null);
            } else {
                log.trace("{}: Adding simple key index", className);
                addIndex(oClass, metaClassWithKey.keyProperty(), true);
            }
        }

        log.trace("{}: Adding indexes for properties", className);
        Streams.fromIterable(metaClass.properties())
                .filter(p -> p.hasAnnotation(Indexable.class) && !p.hasAnnotation(Key.class))
                .forEach(p -> addIndex(oClass, p, p.getAnnotation(Indexable.class).unique()));

        String[] textFields = Streams
                .fromIterable(metaClass.properties())
                .filter(p -> p.hasAnnotation(Searchable.class))
                .map(PropertyMeta::name)
                .toArray(String[]::new);

        if (OrientDbRepository.Properties.isLuceneEnabled() && textFields.length > 0) {
            try {
                ODocument metaData = new ODocument();
                metaData.setProperty("allowLeadingWildcard", true);
                //metaData.setProperty("analyzer", "org.apache.lucene.analysis.core.WhitespaceAnalyzer");
                metaData.setProperty("analyzer", "org.apache.lucene.analysis.core.KeywordAnalyzer");

                oClass.createIndex(className + ".textIndex", "FULLTEXT", null, metaData, "LUCENE", textFields);
            } catch (OIndexException e) {
                log.warn("Full text creation index failed", e);
            }
        }

        log.trace("Class {} creation finished", className);
        return oClass;
    }

    private static void addIndex(OClass oClass, PropertyMeta<?, ?> propertyMeta, boolean unique) {
        log.trace(">> {}: Adding property {} index", oClass.getName(), propertyMeta.name());
        OClass.INDEX_TYPE indexType = unique ? OClass.INDEX_TYPE.UNIQUE_HASH_INDEX : OClass.INDEX_TYPE.NOTUNIQUE_HASH_INDEX;
        String propertyName = PropertyMetas.isEmbedded(propertyMeta)
                ? propertyMeta.name() + "AsString"
                : propertyMeta.name();
        addIndex(oClass, propertyName, indexType);
        log.trace("<< {}: Adding property {} index", oClass.getName(), propertyMeta.name());
    }

    private static void addIndex(OClass oClass, String propertyName, OClass.INDEX_TYPE indexType) {
        log.trace(">> {}: Adding property {} index of type {}", oClass.getName(), propertyName, indexType);
        if (!oClass.areIndexed(propertyName)) {
            oClass.createIndex(oClass.getName() + "." + propertyName + "Index", indexType, propertyName);
        }
        log.trace("<< {}: Adding property {} index of type {}", oClass.getName(), propertyName, indexType);
    }

    private static <T extends HasMetaClass<T>> MetaClass<T> toMetaClass(TypeToken typeToken) {
        //noinspection unchecked
        return MetaClasses.forToken((TypeToken<T>)typeToken);
    }

    private <T> void addProperty(ODatabaseDocument dbSession, OClass oClass, PropertyMeta<T, ?> propertyMeta) {
        OType propertyOType = toOType(propertyMeta.type());
        log.trace("{}: Adding property {} of type {} ({})", oClass.getName(), propertyMeta.name(), propertyMeta.type().getRawType().getSimpleName(), propertyOType);

        if (propertyOType.isLink() || propertyOType.isEmbedded()) {
            OClass linkedOClass = dbSession.getClass(toClassName(propertyMeta.type()));
            if (oClass.existsProperty(propertyMeta.name())) {
                OProperty oProperty = oClass.getProperty(propertyMeta.name());
                if (oProperty.getType() != propertyOType) {
                    oProperty.setType(propertyOType);
                }
                if (!Objects.equals(oProperty.getLinkedClass(), linkedOClass)) {
                    oProperty.setLinkedClass(linkedOClass);
                }
            } else {
                oClass.createProperty(propertyMeta.name(), propertyOType, linkedOClass);
                if (PropertyMetas.isEmbedded(propertyMeta)) {
                    oClass.createProperty(propertyMeta.name() + "AsString", OType.STRING);
                }
            }
        } else {
            if (oClass.existsProperty(propertyMeta.name())) {
                OProperty oProperty = oClass.getProperty(propertyMeta.name());
                if (oProperty.getType() != propertyOType) {
                    oProperty.setType(propertyOType);
                }
            } else {
                oClass.createProperty(propertyMeta.name(), propertyOType);
            }
        }
    }

    private static OType toOType(TypeToken<?> token) {
        Class<?> cls = token.getRawType();
        if (PropertyMetas.isReference(token)) {
            return OType.LINK;
        } else if (PropertyMetas.isEmbedded(token)) {
            return OType.EMBEDDED;
        }
        return OType.getTypeByClass(cls);
    }

    private static String toClassName(MetaClass<?> metaClass) {
        return toClassName(metaClass.asType());
    }

    static String toClassName(TypeToken<?> cls) {
        return toClassName(cls.getRawType());
    }

    private static String toClassName(Class<?> cls) {
        return cls.getSimpleName();
    }
}
