package com.infomaximum.platform.sdk.remote.node;

import com.infomaximum.cluster.event.UpdateNodeConnect;

public interface UpdateNodeConnectService {

    void addListener(UpdateNodeConnect updateNodeListener);

    boolean removeListener(UpdateNodeConnect updateNodeListener);
}
