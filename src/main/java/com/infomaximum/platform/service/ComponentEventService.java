package com.infomaximum.platform.service;

import com.infomaximum.cluster.Node;
import com.infomaximum.cluster.core.component.RuntimeComponentInfo;
import com.infomaximum.cluster.event.CauseNodeDisconnect;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.sdk.component.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static com.infomaximum.platform.sdk.component.ComponentEvent.*;

public class ComponentEventService {

    public final ExecutorService executorsVirtualThreads;
    private final Platform platform;

    public ComponentEventService(Platform platform) {
        this.platform = platform;
        this.executorsVirtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void pushEventOnConnect(Node node) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_CONNECT, Node.class)) {
                        pushEvent(() -> {
                            component.onEventConnect(node);
                            return null;
                        });
                    }
                });
    }

    public void pushEventOnDisconnect(Node node, CauseNodeDisconnect cause) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_DISCONNECT, Node.class, CauseNodeDisconnect.class)) {
                        pushEvent(() -> {
                            component.onEventDisconnect(node, cause);
                            return null;
                        });
                    }
                });
    }

    public void pushEventOnStarted(Node node, RuntimeComponentInfo runtimeComponentInfo) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_STARTED, Node.class, RuntimeComponentInfo.class)) {
                        pushEvent(() -> {
                            component.onEventStarted(node, runtimeComponentInfo);
                            return null;
                        });
                    }
                });
    }

    public void pushEventOnStopped(Node node, RuntimeComponentInfo runtimeComponentInfo) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_STOPPED, Node.class, RuntimeComponentInfo.class)) {
                        pushEvent(() -> {
                            component.onEventStopped(node, runtimeComponentInfo);
                            return null;
                        });
                    }
                });
    }

    public void pushEventOnAvailable(Node node, RuntimeComponentInfo runtimeComponentInfo) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_AVAILABLE, Node.class, RuntimeComponentInfo.class)) {
                        pushEvent(() -> {
                            component.onEventAvailable(node, runtimeComponentInfo);
                            return null;
                        });
                    }
                });
    }

    public void pushEventOnUnavailable(Node node, RuntimeComponentInfo runtimeComponentInfo) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_UNAVAILABLE, Node.class, RuntimeComponentInfo.class)) {
                        pushEvent(() -> {
                            component.onEventUnavailable(node, runtimeComponentInfo);
                            return null;
                        });
                    }
                });
    }

    private void pushEvent(Supplier<Void> eventSupplier) {
        executorsVirtualThreads.execute(() -> {
            try {
                eventSupplier.get();
            } catch (Throwable e) {
                platform.getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        });
    }

    private static boolean isExistMethodInClass(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            clazz.getDeclaredMethod(name, parameterTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
