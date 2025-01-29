package com.infomaximum.platform.sdk.component;

import com.infomaximum.cluster.core.service.transport.executor.ComponentExecutorTransportImpl;
import com.infomaximum.cluster.graphql.remote.graphql.executor.RControllerGraphQLExecutorImpl;
import com.infomaximum.database.anotation.Entity;
import com.infomaximum.database.domainobject.DomainObjectSource;
import com.infomaximum.database.exception.DatabaseException;
import com.infomaximum.database.exception.SchemaException;
import com.infomaximum.database.maintenance.ChangeMode;
import com.infomaximum.database.maintenance.SchemaService;
import com.infomaximum.database.provider.DBProvider;
import com.infomaximum.database.schema.Schema;
import com.infomaximum.database.schema.StructEntity;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.dbprovider.ComponentDBProvider;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.sdk.remote.QueryRemotes;
import com.infomaximum.platform.sdk.remote.component.ComponentEventListenerService;
import com.infomaximum.platform.sdk.remote.node.UpdateNodeConnectService;
import com.infomaximum.platform.sdk.struct.ClusterContext;
import com.infomaximum.platform.sdk.struct.querypool.QuerySystem;
import com.infomaximum.platform.sdk.subscription.GraphQLSubscribeEvent;
import org.reflections.Reflections;

import java.util.HashSet;
import java.util.Set;

public abstract class Component extends com.infomaximum.cluster.struct.Component {

    protected DBProvider dbProvider;
    protected DomainObjectSource domainObjectSource;
    private Schema schema;

    private QueryRemotes queryRemotes;

    private GraphQLSubscribeEvent graphQLSubscribeEvent;
    private RControllerGraphQLExecutorImpl rControllerGraphQLExecutor;

    protected DBProvider initDBProvider() throws PlatformException {
        if (dbProvider != null) {
            return dbProvider;
        }
        return new ComponentDBProvider(getCluster(), this);
    }

    @Override
    protected void registerComponent() {
        super.registerComponent();
        this.rControllerGraphQLExecutor.init();
        onCreate();
    }

    @Override
    protected ComponentExecutorTransportImpl.Builder getExecutorTransportBuilder() {
        ClusterContext clusterContext = getCluster().getContext();
        this.rControllerGraphQLExecutor = clusterContext.platform.getGraphQLEngine().buildRemoteControllerGraphQLExecutor(this);//Обработчик GraphQL запросов
        return super.getExecutorTransportBuilder()
                .withRemoteController(rControllerGraphQLExecutor);
    }

    @Override
    public void destroy() {
        onDestroy();
        super.destroy();
    }

    public void onCreate() {
    }

    public ComponentType getType() {
        return null;
    }

    public UpdateNodeConnectService getNodeConnectService() {
        return Platform.get().getNodeConnectService();
    }

    public ComponentEventListenerService getComponentEventListenerService() {
        return Platform.get().getComponentEventListenerService();
    }

    public QuerySystem<Void> onInstall() {
        return null;
    }

    public QuerySystem<Void> onStart() {
        return null;
    }

    public QuerySystem<Void> onStop() {
        return null;
    }

    public void onDestroy() {
    }

    public void onStarting() throws PlatformException {
        try {
            Set<StructEntity> domains = new HashSet<>();
            for (Class domainObjectClass : new Reflections(getInfo().getUuid()).getTypesAnnotatedWith(Entity.class, true)) {
                domains.add(Schema.getEntity(domainObjectClass));
            }
            schema.checkSubsystemIntegrity(domains, getInfo().getUuid());
            buildSchemaService()
                    .setChangeMode(ChangeMode.CREATION)
                    .setValidationMode(false)
                    .execute();
        } catch (DatabaseException e) {
            throw GeneralExceptionBuilder.buildDatabaseException(e);
        }

        this.graphQLSubscribeEvent = new GraphQLSubscribeEvent(this);
    }

    public SchemaService buildSchemaService() {
        return new SchemaService(getDbProvider())
                .setNamespace(getInfo().getUuid())
                .setSchema(getSchema());
    }

    public final DBProvider getDbProvider() {
        return dbProvider;
    }

    public final DomainObjectSource getDomainObjectSource() {
        return domainObjectSource;
    }

    public Schema getSchema() {
        return schema;
    }

    public void initialize() throws PlatformException {
        if (!getClass().getPackage().getName().equals(getInfo().getUuid())) {
            throw new RuntimeException(getClass() + " is not correspond to uuid: " + getInfo().getUuid());
        }

        this.dbProvider = initDBProvider();
        this.schema = initializeSchema(dbProvider);

        this.domainObjectSource = new DomainObjectSource(dbProvider, true);

        this.queryRemotes = new QueryRemotes(this);
    }

    //TODO Ulitin V. Временное решение - после переноса механизма обновления в платформу - убрать
    public void reloadSchema(DBProvider dbProvider) {
        try {
            this.schema = Schema.read(dbProvider);
        } catch (DatabaseException e) {
            throw new SchemaException(e);
        }
    }

    public final QueryRemotes getQueryRemotes() {
        return queryRemotes;
    }

    public final GraphQLSubscribeEvent getGraphQLSubscribeEvent() {
        return graphQLSubscribeEvent;
    }

    private Schema initializeSchema(DBProvider dbProvider) {
        try {
            Schema schema;
            if (Schema.exists(dbProvider)) {
                schema = Schema.read(dbProvider);
            } else {
                schema = Schema.create(dbProvider);
            }
            for (Class domainObjectClass : new Reflections(getInfo().getUuid()).getTypesAnnotatedWith(Entity.class, true)) {
                Schema.resolve(domainObjectClass); //todo убрать resolve когда переведу функционал из StructEntity
            }
            return schema;
        } catch (DatabaseException e) {
            throw new SchemaException(e);
        }
    }
}
