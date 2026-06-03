package com.infomaximum.platform.service;

import com.infomaximum.cluster.Node;
import com.infomaximum.cluster.component.manager.event.EventUpdateComponent;
import com.infomaximum.cluster.core.component.RuntimeComponentInfo;
import com.infomaximum.cluster.event.CauseNodeDisconnect;
import com.infomaximum.cluster.event.UpdateNodeConnect;

public class ComponentEventNodeConnect implements UpdateNodeConnect, EventUpdateComponent {

    private final ComponentEventService componentEventService;

    public ComponentEventNodeConnect(ComponentEventService componentEventService) {
        this.componentEventService = componentEventService;
    }

    @Override
    public void onConnect(Node node) {
        componentEventService.pushEventOnConnect(node);
    }

    @Override
    public void onDisconnect(Node node, CauseNodeDisconnect cause) {
        componentEventService.pushEventOnDisconnect(node, cause);
    }

    @Override
    public void registerRemoteComponent(Node node, RuntimeComponentInfo subSystemInfo) {
        componentEventService.pushEventOnAvailable(node, subSystemInfo);
    }

    @Override
    public void startRemoteComponent(Node node, RuntimeComponentInfo subSystemInfo) {
        componentEventService.pushEventOnStarted(node, subSystemInfo);
        componentEventService.pushEventOnAvailable(node, subSystemInfo);
    }

    @Override
    public void unRegisterRemoteComponent(Node node, RuntimeComponentInfo subSystemInfo) {
        componentEventService.pushEventOnUnavailable(node, subSystemInfo);
    }

    @Override
    public void registerLocalComponent(RuntimeComponentInfo subSystemInfo) {

    }

    @Override
    public void unRegisterLocalComponent(RuntimeComponentInfo subSystemInfo) {

    }
}
