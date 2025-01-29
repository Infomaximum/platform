package com.infomaximum.platform.sdk.remote.component;

import com.infomaximum.platform.sdk.component.ComponentEventListener;

public interface ComponentEventListenerService {

    void addListener(ComponentEventListener componentEventListener);

    boolean removeListener(ComponentEventListener componentEventListener);
}
