package com.infomaximum.platform.service;

import com.infomaximum.cluster.Node;
import com.infomaximum.platform.sdk.component.ComponentEventListener;
import com.infomaximum.platform.sdk.component.ComponentInfo;
import com.infomaximum.platform.sdk.remote.component.ComponentEventListenerService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ComponentEventListenerServiceImpl implements ComponentEventListener, ComponentEventListenerService {

    private final List<ComponentEventListener> componentEventListeners;

    public ComponentEventListenerServiceImpl() {
        componentEventListeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public void addListener(ComponentEventListener componentEventListener) {
        componentEventListeners.add(componentEventListener);
    }

    @Override
    public boolean removeListener(ComponentEventListener componentEventListener) {
        return componentEventListeners.remove(componentEventListener);
    }

    @Override
    public void onBeforeStart(Node node, ComponentInfo componentInfo) {
        componentEventListeners.forEach(componentEventListener -> componentEventListener.onBeforeStart(node, componentInfo));
    }

    @Override
    public void onAfterStart(Node node, ComponentInfo componentInfo) {
        componentEventListeners.forEach(componentEventListener -> componentEventListener.onAfterStart(node, componentInfo));
    }

    @Override
    public void onAllStart(Node node) {
        componentEventListeners.forEach(componentEventListener -> componentEventListener.onAllStart(node));
    }

    @Override
    public void onBeforeStop(Node node, ComponentInfo componentInfo) {
        componentEventListeners.forEach(componentEventListener -> componentEventListener.onBeforeStop(node, componentInfo));
    }

    @Override
    public void onAfterStop(Node node, ComponentInfo componentInfo) {
        componentEventListeners.forEach(componentEventListener -> componentEventListener.onAfterStop(node, componentInfo));
    }

    @Override
    public void onAllStop(Node node) {
        componentEventListeners.forEach(componentEventListener ->  componentEventListener.onAllStop(node));
    }
}
