package com.infomaximum.platform.service;

import com.infomaximum.cluster.Node;
import com.infomaximum.cluster.event.CauseNodeDisconnect;
import com.infomaximum.cluster.event.UpdateNodeConnect;
import com.infomaximum.platform.sdk.remote.node.UpdateNodeConnectService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UpdateNodeConnectServiceImpl implements UpdateNodeConnect, UpdateNodeConnectService {

    private final List<UpdateNodeConnect> updateNodeListeners;

    public UpdateNodeConnectServiceImpl() {
        updateNodeListeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public void onConnect(Node node) {
        updateNodeListeners.forEach(updateNodeConnect -> updateNodeConnect.onConnect(node));
    }

    @Override
    public void onDisconnect(Node node, CauseNodeDisconnect cause) {
        updateNodeListeners.forEach(updateNodeConnect -> updateNodeConnect.onDisconnect(node, cause));
    }

    @Override
    public void addListener(UpdateNodeConnect updateNodeListener) {
        updateNodeListeners.add(updateNodeListener);
    }

    @Override
    public boolean removeListener(UpdateNodeConnect updateNodeListener) {
        return updateNodeListeners.remove(updateNodeListener);
    }
}
