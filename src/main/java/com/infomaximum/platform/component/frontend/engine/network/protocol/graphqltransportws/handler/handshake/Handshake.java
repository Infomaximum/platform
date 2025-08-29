package com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.handler.handshake;

import com.infomaximum.network.packet.IPacket;
import com.infomaximum.network.protocol.PacketHandler;
import com.infomaximum.network.protocol.standard.packet.ResponsePacket;
import com.infomaximum.network.session.Session;
import com.infomaximum.network.session.SessionImpl;
import com.infomaximum.network.struct.HandshakeData;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.packet.Packet;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.packet.TypePacket;
import com.infomaximum.platform.exception.PlatformException;

import java.util.concurrent.CompletableFuture;

public abstract class Handshake implements PacketHandler {

    public record HandshakeCompletable(Response response, HandshakeData handshakeData){}

    public Handshake() {
    }

    public void onPhaseHandshake(Session session) {

    }

    /**
     * Завершаем фазу рукопожатия
     *
     * @param session
     */
    public void completedPhaseHandshake(Session session, HandshakeData handshakeData) {
        ((SessionImpl)session).getTransportSession().completedPhaseHandshake(handshakeData);
    }

    /**
     * Ошибка фазы рукопожатия - разрываем соединение
     *
     * @param session
     */
    public void failPhaseHandshake(Session session, ResponsePacket responsePacket) {
        ((SessionImpl)session).getTransportSession().failPhaseHandshake(responsePacket);
    }

    public abstract HandshakeCompletable handshake(Packet packet) throws PlatformException;

    @Override
    public CompletableFuture<IPacket[]> exec(Session session, IPacket packet) {
        Packet requestPacket = (Packet) packet;
        Packet responsePacket;
        if (requestPacket.type == TypePacket.GQL_CONNECTION_INIT) {
            try {
                HandshakeCompletable handshakeCompletable = handshake(requestPacket);
                completedPhaseHandshake(session, handshakeCompletable.handshakeData);
                responsePacket = new Packet(requestPacket.id, TypePacket.GQL_CONNECTION_ACK, handshakeCompletable.response.payload());
            } catch (PlatformException e) {
                responsePacket = new Packet(requestPacket.id, TypePacket.GQL_CONNECTION_ERROR);
            }
        } else {
            responsePacket = new Packet(requestPacket.id, TypePacket.GQL_CONNECTION_ERROR);
        }
        return CompletableFuture.completedFuture(new IPacket[]{ responsePacket });
    }

}
