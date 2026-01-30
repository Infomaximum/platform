package com.infomaximum.testcomponent;

import com.infomaximum.cluster.Node;

import java.util.UUID;

public class RemoteNode implements Node {

    private final UUID runtimeId;

    public RemoteNode() {
        runtimeId = UUID.randomUUID();
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public String getName() {
        return "remoteNode";
    }

    @Override
    public UUID getRuntimeId() {
        return runtimeId;
    }
}
