package com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws;

import com.infomaximum.network.protocol.Protocol;
import com.infomaximum.network.protocol.ProtocolBuilder;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.handler.graphql.GraphQLWSHandler;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.handler.handshake.DefaultHandshake;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.handler.handshake.Handshake;

public class GraphqlWSProtocolBuilder extends ProtocolBuilder {

    private final GraphQLWSHandler packetHandler;
    private Handshake handshake;

    public GraphqlWSProtocolBuilder(GraphQLWSHandler packetHandler) {
        this.packetHandler = packetHandler;
        this.handshake = new DefaultHandshake();
    }

    public GraphqlWSProtocolBuilder withHandshake(Handshake handshake) {
        this.handshake = handshake;
        return this;
    }

    @Override
    public Protocol build(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        return new GraphqlWSProtocol(handshake, packetHandler, uncaughtExceptionHandler);
    }
}
