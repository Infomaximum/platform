package com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.handler.handshake;

import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.packet.Packet;
import net.minidev.json.JSONObject;


public class DefaultHandshake extends Handshake {

    @Override
    public HandshakeCompletable handshake(Packet packet) {
        String xTraceId = null;
        if (packet.payload instanceof JSONObject jPayload) {
            xTraceId = jPayload.getAsString("x_trace_id");
        }
        return new HandshakeCompletable(new Response(null), new HandshakeDataImpl(xTraceId)) ;
    }
}
