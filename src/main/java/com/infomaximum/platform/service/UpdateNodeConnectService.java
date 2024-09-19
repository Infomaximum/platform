package com.infomaximum.platform.service;

import com.infomaximum.cluster.Node;
import com.infomaximum.cluster.event.CauseNodeDisconnect;
import com.infomaximum.cluster.event.UpdateNodeConnect;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class UpdateNodeConnectService implements UpdateNodeConnect {

    private final List<UpdateNodeConnect> updateNodeListeners;

    public UpdateNodeConnectService() {
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

    public void addListener(UpdateNodeConnect updateNodeListener) {
        updateNodeListeners.add(updateNodeListener);
    }

    public boolean removeListener(UpdateNodeConnect updateNodeListener) {
        return updateNodeListeners.remove(updateNodeListener);
    }
}
