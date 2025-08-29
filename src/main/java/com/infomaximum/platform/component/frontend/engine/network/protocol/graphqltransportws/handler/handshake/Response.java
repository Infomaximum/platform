package com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.handler.handshake;

import com.infomaximum.network.struct.HandshakeData;
import net.minidev.json.JSONObject;

public record Response(JSONObject payload) {
}
