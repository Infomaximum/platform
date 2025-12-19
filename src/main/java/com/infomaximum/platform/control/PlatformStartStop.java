package com.infomaximum.platform.control;

import com.infomaximum.database.exception.DatabaseException;
import com.infomaximum.database.schema.Schema;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.component.database.DatabaseComponent;
import com.infomaximum.platform.component.database.info.DBInfo;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.querypool.Query;
import com.infomaximum.platform.querypool.QueryTransaction;
import com.infomaximum.platform.querypool.ResourceProvider;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.sdk.component.ComponentEventListener;
import com.infomaximum.platform.sdk.component.ComponentType;
import com.infomaximum.platform.sdk.context.ContextTransaction;
import com.infomaximum.platform.sdk.context.impl.ContextTransactionImpl;
import com.infomaximum.platform.sdk.context.source.impl.SourceSystemImpl;
import com.infomaximum.platform.sdk.domainobject.module.ModuleReadable;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.sdk.struct.querypool.QuerySystem;
import com.infomaximum.rocksdb.RocksDBProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PlatformStartStop {

    private final static Logger log = LoggerFactory.getLogger(PlatformStartStop.class);

    private final Platform platform;

    private final ComponentEventListener componentEventListener;

    public PlatformStartStop(Platform platform, ComponentEventListener componentEventListener) {
        this.platform = platform;
        this.componentEventListener = componentEventListener;
    }

    /**
     * Запуск происходит в несколько фаз:
     * 1) onStarting - инициализирующая фаза старта компонента
     * 2) onStart - Все необходимые фазы пройденны - пользовательский запуск
     *
     * checkUpgrade - флаг указывающий что старт "урезанный", для проверки обновления
     * @throws PlatformException
     */
    public void start(boolean checkUpgrade) throws PlatformException {
        //initialize
        initialize();

        //onStarting
        for (Component component : platform.getCluster().getDependencyOrderedComponentsOf(Component.class)) {
            component.onStarting();
        }

        //Режим обновления, упрощенный режим старт/стоп
        if (checkUpgrade) {
            return;
        }

        //onStart
        DatabaseComponent databaseComponent = platform.getCluster().getAnyLocalComponent(DatabaseComponent.class);
        List<Component> componentList = platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .stream()
                .sorted((o1, o2) -> {//Необходимо, что бы фронт запустился самым последним
                    if (o1.getType() == ComponentType.FRONTEND) {
                        return 1;
                    } else if (o2.getType() == ComponentType.FRONTEND) {
                        return -1;
                    } else {
                        return 0;
                    }
                }).toList();
        for (Component component : componentList) {
            QuerySystem<Void> querySystem = component.onStart();
            if (querySystem != null) {
                executeQuerySystem(databaseComponent, querySystem);
            }
            component.onStarted();
        }
    }

    /**

     * @param checkUpgrade - флаг указывающий что старт был "урезанный", для проверки обновления, поэтому и onStop вызывать ен следует
     * @throws PlatformException
     */
    public void stop(boolean checkUpgrade) throws PlatformException {
        //Режим проверки обновления, упрощенный режим старт/стоп
        if (checkUpgrade) {
            return;
        }

        //onStop
        List<Component> reverseDependencyOrderedComponents = platform.getCluster().getDependencyOrderedComponentsOf(Component.class);
        Collections.reverse(reverseDependencyOrderedComponents);
        Collections.sort(reverseDependencyOrderedComponents, (o1, o2) -> {//Необходимо, что бы фронт остановился самым первым
            if (o1.getType() == ComponentType.FRONTEND) {
                return -1;
            } else if (o2.getType() == ComponentType.FRONTEND) {
                return 1;
            } else {
                return 0;
            }
        });
        DatabaseComponent databaseComponent = platform.getCluster().getAnyLocalComponent(DatabaseComponent.class);
        for (Component component : reverseDependencyOrderedComponents) {
            QuerySystem<Void> querySystem = component.onStop();
            if (querySystem != null) {
                executeQuerySystem(databaseComponent, querySystem);
            }
        }
    }

    private void executeQuerySystem(DatabaseComponent databaseComponent, QuerySystem<Void> querySystem) throws PlatformException {
        try {
            platform.getQueryPool().execute(databaseComponent, new Query<Void>() {

                @Override
                public void prepare(ResourceProvider resources) throws PlatformException {
                    querySystem.prepare(resources);
                }

                @Override
                public Void execute(QueryTransaction transaction) throws PlatformException {
                    ContextTransaction contextTransaction = new ContextTransactionImpl(new SourceSystemImpl(), transaction);
                    querySystem.execute(contextTransaction);
                    return null;
                }
            }).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PlatformException) {
                throw (PlatformException) cause;
            } else {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void initialize() throws PlatformException {
        for (Component component : platform.getCluster().getDependencyOrderedComponentsOf(Component.class)) {
            if (component.getDbProvider() == null) {
                component.initialize();
            }
        }
        DatabaseComponent databaseSubsystem = platform.getCluster().getAnyLocalComponent(DatabaseComponent.class);
        RocksDBProvider rocksDBProvider = databaseSubsystem.getRocksDBProvider();

        // Создаем объект info в service_column_family
        DBInfo.createIfNotExists(rocksDBProvider);

        //Инициализируем ModuleReadable
        try {
            Schema schema = Schema.read(rocksDBProvider);
            log.warn("Schema on start: " + schema.getDbSchema().toTablesJsonString());
            Schema.resolve(ModuleReadable.class);
        } catch (DatabaseException e) {
            throw GeneralExceptionBuilder.buildDatabaseException(e);
        }
    }
}
