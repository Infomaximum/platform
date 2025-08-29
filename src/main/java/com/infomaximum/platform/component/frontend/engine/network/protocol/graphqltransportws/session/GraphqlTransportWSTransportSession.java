package com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.session;

import com.infomaximum.network.exception.ParsePacketNetworkException;
import com.infomaximum.network.packet.IPacket;
import com.infomaximum.network.protocol.PacketHandler;
import com.infomaximum.network.session.SessionImpl;
import com.infomaximum.network.session.TransportSession;
import com.infomaximum.network.struct.HandshakeData;
import com.infomaximum.network.struct.UpgradeRequest;
import com.infomaximum.network.transport.Transport;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.GraphqlTransportWSProtocol;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.handler.handshake.HandshakeDataImpl;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.packet.Packet;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;

public class GraphqlTransportWSTransportSession extends TransportSession {

    private final static Logger log = LoggerFactory.getLogger(GraphqlTransportWSTransportSession.class);

    //Флаг определяеющий что мы в фазе рукопожатия
    private volatile boolean phaseHandshake;

    public GraphqlTransportWSTransportSession(GraphqlTransportWSProtocol protocol, final Transport transport, final Object channel, UpgradeRequest upgradeRequest) {
        super(protocol, transport, channel, upgradeRequest);

        //Проверяем наличие фазы рукопожатия
        if (protocol.handshake != null) {
            phaseHandshake = true;
        } else {
            phaseHandshake = false;
//			network.onHandshake(session);
        }
    }

    @Override
    public IPacket parse(String message) throws ParsePacketNetworkException {
        try {
            JSONObject incoming = (JSONObject) new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(message);
            return Packet.parse(incoming);
        } catch (ParseException e) {
            throw new ParsePacketNetworkException(e);
        }
    }


    @Override
    public void completedPhaseHandshake(HandshakeData handshakeData) {
        phaseHandshake = false;
        ((SessionImpl)session).initHandshakeData(handshakeData);
    }

    @Override
    public void failPhaseHandshake(IPacket responsePacket) {

    }

    @Override
    public String getXTraceId() {
        return ((HandshakeDataImpl)session.getHandshakeData()).xTraceId;
    }

    @Override
    public boolean isPhaseHandshake() {
        return phaseHandshake;
    }

    /**
     * Возврощаем обработчика пакетов
     *
     * @return
     */
    @Override
    public PacketHandler getPacketHandler() {
        GraphqlTransportWSProtocol graphqlWSProtocol = (GraphqlTransportWSProtocol) protocol;
        if (phaseHandshake) {
            return graphqlWSProtocol.handshake;
        } else {
            return graphqlWSProtocol.packetHandler;
        }
    }
}
