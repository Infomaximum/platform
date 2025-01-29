package com.infomaximum.platform.sdk.component;

import com.infomaximum.cluster.Node;

public interface ComponentEventListener {

    default void onBeforeStart(Node node, ComponentInfo componentInfo) {}

    default void onAfterStart(Node node, ComponentInfo componentInfo) {}

    default void onAllStart(Node node) {}

    default void onBeforeStop(Node node, ComponentInfo componentInfo) {}

    default void onAfterStop(Node node, ComponentInfo componentInfo) {}

    default void onAllStop(Node node) {}
}
