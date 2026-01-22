package com.infomaximum.platform.sdk.component;

import com.infomaximum.cluster.Node;
import com.infomaximum.cluster.core.component.RuntimeComponentInfo;
import com.infomaximum.cluster.event.CauseNodeDisconnect;

public interface ComponentEvent {

    String METHOD_ON_EVENT_CONNECT = "onEventConnect";
    String METHOD_ON_EVENT_DISCONNECT = "onEventDisconnect";
    String METHOD_ON_EVENT_STARTED = "onEventStarted";
    String METHOD_ON_EVENT_STOPPED = "onEventStopped";
    String METHOD_ON_EVENT_AVAILABLE = "onEventAvailable";
    String METHOD_ON_EVENT_UNAVAILABLE = "onEventUnavailable";

    default void onEventConnect(Node node) {}

    default void onEventDisconnect(Node node, CauseNodeDisconnect cause) {}

    default void onEventStarted(Node node, RuntimeComponentInfo componentInfo) {}

    default void onEventStopped(Node node, RuntimeComponentInfo componentInfo) {}

    default void onEventAvailable(Node node, RuntimeComponentInfo componentInfo) {}

    default void onEventUnavailable(Node node, RuntimeComponentInfo componentInfo) {}
}
