package com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.handler.graphql;

import com.infomaximum.cluster.graphql.executor.struct.GSubscriptionPublisher;
import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.network.packet.IPacket;
import com.infomaximum.network.protocol.PacketHandler;
import com.infomaximum.network.session.Session;
import com.infomaximum.network.session.SessionImpl;
import com.infomaximum.network.struct.RemoteAddress;
import com.infomaximum.network.struct.UpgradeRequest;
import com.infomaximum.platform.component.frontend.engine.network.protocol.GraphQLSubscriber;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.packet.Packet;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.packet.TypePacket;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.subscriber.WebSocketGraphQLWSSubscriber;
import com.infomaximum.platform.component.frontend.engine.provider.ProviderGraphQLRequestExecuteService;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.component.frontend.request.GRequestWebSocket;
import net.minidev.json.JSONObject;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GraphQLWSHandler implements PacketHandler {

    private final ProviderGraphQLRequestExecuteService providerGraphQLRequestExecuteService;

    private final GraphQLSubscriber graphQLSubscriber;

    public GraphQLWSHandler(ProviderGraphQLRequestExecuteService providerGraphQLRequestExecuteService) {
        this.providerGraphQLRequestExecuteService = providerGraphQLRequestExecuteService;
        this.graphQLSubscriber = new GraphQLSubscriber();
    }


    @Override
    public CompletableFuture<IPacket[]> exec(Session session, IPacket packet) {
        Packet requestPacket = (Packet) packet;
        TypePacket typePacket = requestPacket.type;

        if (typePacket == TypePacket.GQL_START) {
            return execGraphQL(session, requestPacket);
        } else {
            return CompletableFuture.completedFuture(new IPacket[]{
                    new Packet(requestPacket.id, TypePacket.GQL_ERROR)
            });
        }
    }

    private CompletableFuture<IPacket[]> execGraphQL(Session session, Packet requestPacket) {
        JSONObject payload = requestPacket.payload;

        String query = payload.getAsString("query");
        if (query == null || query.trim().isEmpty()) {
            return CompletableFuture.completedFuture(new IPacket[]{
                    new Packet(requestPacket.id, TypePacket.GQL_ERROR)
            });
        }

        HashMap<String, Serializable> variables;
        JSONObject jVariables = (JSONObject) payload.get("variables");
        if (jVariables != null) {
            variables = new HashMap<>((Map) jVariables);
        } else {
            variables = new HashMap<>();
        }

        String operationName =  payload.getAsString("operation_name");

        UpgradeRequest upgradeRequest = ((SessionImpl)session).getTransportSession().getUpgradeRequest();

        Map<String, String> parameters = upgradeRequest.getParameters();

        RemoteAddress remoteAddress = ((SessionImpl)session).getTransportSession().buildRemoteAddress();
        String xTraceId = ((SessionImpl) session).getTransportSession().getXTraceId();

        GRequestWebSocket gRequest = new GRequestWebSocket(
                Instant.now(),
                new GRequest.RemoteAddress(remoteAddress.getRawRemoteAddress(), remoteAddress.getEndRemoteAddress()),
                query, variables, operationName,
                xTraceId,
                session.getUuid(),
                parameters,
                upgradeRequest.getCookies(),
                session.getHandshakeData()
        );

        return providerGraphQLRequestExecuteService.getGraphQLRequestExecuteService()
                .execute(gRequest)
                .thenCompose(graphQLResponse -> buildResponsePacket(graphQLResponse, session, requestPacket));
    }

    private CompletableFuture<IPacket[]> buildResponsePacket(
            GraphQLResponse graphQLResponse,
            Session session, Packet requestPacket
    ) {
        if (graphQLResponse.error) {
            JSONObject jPayload = new JSONObject();
            jPayload.put("errors", graphQLResponse.data);
            return CompletableFuture.completedFuture(new IPacket[]{
                    new Packet(requestPacket.id, TypePacket.GQL_ERROR, jPayload)
            });
        } else {
            Object data = graphQLResponse.data;
            if (data instanceof JSONObject) {
                JSONObject jPayload = new JSONObject();
                jPayload.put("data", graphQLResponse.data);
                return CompletableFuture.completedFuture(new IPacket[]{
                        new Packet(requestPacket.id, TypePacket.GQL_DATA, jPayload)
                });
            } else if (data instanceof GSubscriptionPublisher completionPublisher) {
                WebSocketGraphQLWSSubscriber websocketSubscriber = new WebSocketGraphQLWSSubscriber(graphQLSubscriber, requestPacket.id, ((SessionImpl)session).getTransportSession());
                completionPublisher.subscribe(websocketSubscriber);

                CompletableFuture<IPacket[]> result = new CompletableFuture<>();
                websocketSubscriber.getFirstResponseCompletableFuture()
                        .thenApply(iPacket -> result.complete(new IPacket[]{iPacket}));
                return result;
            } else {
                throw new RuntimeException("Not support type out: " + data);
            }
        }
    }

}
