package com.infomaximum.platform.control;

import com.infomaximum.cluster.Node;
import com.infomaximum.database.exception.DatabaseException;
import com.infomaximum.database.schema.Schema;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.component.database.DatabaseComponent;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.querypool.Query;
import com.infomaximum.platform.querypool.QueryTransaction;
import com.infomaximum.platform.querypool.ResourceProvider;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.sdk.component.ComponentEventListener;
import com.infomaximum.platform.sdk.component.ComponentInfo;
import com.infomaximum.platform.sdk.component.ComponentType;
import com.infomaximum.platform.sdk.context.ContextTransaction;
import com.infomaximum.platform.sdk.context.impl.ContextTransactionImpl;
import com.infomaximum.platform.sdk.context.source.impl.SourceSystemImpl;
import com.infomaximum.platform.sdk.domainobject.module.ModuleReadable;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.sdk.struct.querypool.QuerySystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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

        //TODO Ulitin V. - где то потерялась валидация схемы база данных!!!

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
        List<ComponentQuery> startQueries = platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .stream()
                .sorted((o1, o2) -> {//Необходимо, что бы фронт запустился самым последним
                    if (o1.getType() == ComponentType.FRONTEND) {
                        return 1;
                    } else if (o2.getType() == ComponentType.FRONTEND) {
                        return -1;
                    } else {
                        return 0;
                    }
                })
                .map(component -> new ComponentQuery(component.getInfo().getUuid(), component.onStart()))
                .collect(Collectors.toList());
        Node node = platform.getCluster().node;
        try {
            platform.getQueryPool().execute(databaseComponent, new Query<Void>() {

                @Override
                public void prepare(ResourceProvider resources) throws PlatformException {
                    for (ComponentQuery componentQuery : startQueries) {
                        if (componentQuery.querySystem != null) {
                            componentQuery.querySystem.prepare(resources);
                        }
                    }
                }

                @Override
                public Void execute(QueryTransaction transaction) throws PlatformException {
                    ContextTransaction contextTransaction = new ContextTransactionImpl(new SourceSystemImpl(), transaction);
                    for (ComponentQuery componentQuery : startQueries) {
                        componentEventListener.onBeforeStart(node, new ComponentInfo(componentQuery.componentUuid));
                        if (componentQuery.querySystem != null) {
                            componentQuery.querySystem.execute(contextTransaction);
                        }
                        componentEventListener.onAfterStart(node, new ComponentInfo(componentQuery.componentUuid));
                    }
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
        componentEventListener.onAllStart(node);
    }

    public void initialize() throws PlatformException {
        for (Component component : platform.getCluster().getDependencyOrderedComponentsOf(Component.class)) {
            if (component.getDbProvider() == null) {
                component.initialize();
            }
        }

        //Инициализируем ModuleReadable
        try {
            DatabaseComponent databaseSubsystem = platform.getCluster().getAnyLocalComponent(DatabaseComponent.class);
            Schema schema = Schema.read(databaseSubsystem.getRocksDBProvider());
            log.warn("Schema on start: " + schema.getDbSchema().toTablesJsonString());
            Schema.resolve(ModuleReadable.class);
        } catch (DatabaseException e) {
            throw GeneralExceptionBuilder.buildDatabaseException(e);
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

        List<Component> reverseDependencyOrderedComponents = new ArrayList(platform.getCluster().getDependencyOrderedComponentsOf(Component.class));
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
        List<ComponentQuery> stopQueries = reverseDependencyOrderedComponents
                .stream()
                .map(component -> new ComponentQuery(component.getInfo().getUuid(), component.onStop()))
                .collect(Collectors.toList());
        Node node = platform.getCluster().node;
        try {
            DatabaseComponent databaseComponent = platform.getCluster().getAnyLocalComponent(DatabaseComponent.class);
            platform.getQueryPool().execute(databaseComponent, new Query<Void>() {
                @Override
                public void prepare(ResourceProvider resources) throws PlatformException {
                    for (ComponentQuery componentQuery : stopQueries) {
                        if (componentQuery.querySystem != null) {
                            componentQuery.querySystem.prepare(resources);
                        }
                    }
                }

                @Override
                public Void execute(QueryTransaction transaction) throws PlatformException {
                    ContextTransaction contextTransaction = new ContextTransactionImpl(new SourceSystemImpl(), transaction);
                    for (ComponentQuery componentQuery : stopQueries) {
                        componentEventListener.onBeforeStop(node, new ComponentInfo(componentQuery.componentUuid));
                        if (componentQuery.querySystem != null) {
                            componentQuery.querySystem.execute(contextTransaction);
                        }
                        componentEventListener.onAfterStop(node, new ComponentInfo(componentQuery.componentUuid));
                    }
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
        componentEventListener.onAllStop(node);
    }

    private record ComponentQuery(String componentUuid, QuerySystem<Void> querySystem){}
}
