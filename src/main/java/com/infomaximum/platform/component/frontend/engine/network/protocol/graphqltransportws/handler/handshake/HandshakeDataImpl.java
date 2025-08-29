package com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.handler.handshake;

import com.infomaximum.network.struct.HandshakeData;

public class HandshakeDataImpl implements HandshakeData {

    public final String xTraceId;

    public HandshakeDataImpl(String xTraceId) {
        this.xTraceId = xTraceId;
    }
}
