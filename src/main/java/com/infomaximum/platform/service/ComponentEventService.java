package com.infomaximum.platform.service;

import com.infomaximum.cluster.Node;
import com.infomaximum.cluster.core.component.RuntimeComponentInfo;
import com.infomaximum.cluster.event.CauseNodeDisconnect;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.sdk.component.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.infomaximum.platform.sdk.component.ComponentEvent.*;
import static com.infomaximum.platform.service.ComponentEventQueue.*;

public class ComponentEventService {

    private static final ConcurrentHashMap<MethodCacheKey, Boolean> methodCache = new ConcurrentHashMap<>();
    private final Platform platform;

    public ComponentEventService(Platform platform) {
        this.platform = platform;
    }

    public void pushEventOnConnect(Node node) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_CONNECT, Node.class)) {
                        Event event = new Event(METHOD_ON_EVENT_CONNECT, node, () -> component.onEventConnect(node));
                        component.getComponentEventQueue(platform).pushEvent(event);
                    }
                });
    }

    public void pushEventOnDisconnect(Node node, CauseNodeDisconnect cause) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_DISCONNECT, Node.class, CauseNodeDisconnect.class)) {
                        Event event = new Event(METHOD_ON_EVENT_DISCONNECT, node, () -> component.onEventDisconnect(node, cause));
                        component.getComponentEventQueue(platform).pushEvent(event);
                    }
                });
    }

    public void pushEventOnStarted(Node node, RuntimeComponentInfo runtimeComponentInfo) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_STARTED, Node.class, RuntimeComponentInfo.class)) {
                        Event event = new Event(METHOD_ON_EVENT_STARTED, node, () -> component.onEventStarted(node, runtimeComponentInfo));
                        component.getComponentEventQueue(platform).pushEvent(event);
                    }
                });
    }

    public void pushEventOnStopped(Node node, RuntimeComponentInfo runtimeComponentInfo) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_STOPPED, Node.class, RuntimeComponentInfo.class)) {
                        Event event = new Event(METHOD_ON_EVENT_STOPPED, node, () -> component.onEventStopped(node, runtimeComponentInfo));
                        component.getComponentEventQueue(platform).pushEvent(event);
                    }
                });
    }

    public void pushEventOnAvailable(Node node, RuntimeComponentInfo runtimeComponentInfo) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_AVAILABLE, Node.class, RuntimeComponentInfo.class)) {
                        Event event = new Event(METHOD_ON_EVENT_AVAILABLE, node, () -> component.onEventAvailable(node, runtimeComponentInfo));
                        component.getComponentEventQueue(platform).pushEvent(event);
                    }
                });
    }

    public void pushEventOnUnavailable(Node node, RuntimeComponentInfo runtimeComponentInfo) {
        platform.getCluster().getDependencyOrderedComponentsOf(Component.class)
                .forEach(component -> {
                    if (isExistMethodInClass(component.getClass(), METHOD_ON_EVENT_UNAVAILABLE, Node.class, RuntimeComponentInfo.class)) {
                        Event event = new Event(METHOD_ON_EVENT_UNAVAILABLE, node, () -> component.onEventUnavailable(node, runtimeComponentInfo));
                        component.getComponentEventQueue(platform).pushEvent(event);
                    }
                });
    }

    private static boolean isExistMethodInClass(Class<?> clazz, String name, Class<?>... parameterTypes) {
        return methodCache.computeIfAbsent(new MethodCacheKey(clazz, name, List.of(parameterTypes)), k -> {
            try {
                // getMethod() находит и дефолтные реализации интерфейса ComponentEvent —
                // isInterface() отсеивает их, оставляя только реальные переопределения
                return !clazz.getMethod(name, parameterTypes).getDeclaringClass().isInterface();
            } catch (NoSuchMethodException e) {
                return false;
            }
        });
    }

    private record MethodCacheKey(Class<?> clazz, String methodName, List<Class<?>> parameterTypes) {}
}
