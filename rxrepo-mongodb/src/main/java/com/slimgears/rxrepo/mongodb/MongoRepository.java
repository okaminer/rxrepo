package com.slimgears.rxrepo.mongodb;

import com.slimgears.rxrepo.query.Repository;
import com.slimgears.rxrepo.query.decorator.LiveQueryProviderDecorator;
import com.slimgears.rxrepo.query.decorator.UpdateReferencesFirstQueryProviderDecorator;
import com.slimgears.rxrepo.query.provider.QueryProvider;
import com.slimgears.util.generic.MoreStrings;

public class MongoRepository {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String dbName = "repository";
        private String host = "localhost";
        private int port = 27017;
        private String user = null;
        private String password = null;
        private QueryProvider.Decorator decorator = QueryProvider.Decorator.identity();

        private Builder() {
            decorate(
                    LiveQueryProviderDecorator.decorator(),
                    UpdateReferencesFirstQueryProviderDecorator.decorator());
        }

        public Builder dbName(String dbName) {
            this.dbName = dbName;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        Builder decorate(QueryProvider.Decorator... decorators) {
            decorator = QueryProvider.Decorator.of(this.decorator, QueryProvider.Decorator.of(decorators));
            return this;
        }

        public Repository build() {
            String connectionString = createConnectionString();
            QueryProvider queryProvider = new MongoQueryProvider(connectionString, dbName);
            queryProvider = decorator.apply(queryProvider);
            return Repository.fromProvider(queryProvider);
        }

        private String createConnectionString() {
            return user != null && password != null
                    ? MoreStrings.format("mongodb://{}:{}@{}:{}", user, password, host, port)
                    : MoreStrings.format("mongodb://{}:{}/{}", host, port);
        }
    }
}