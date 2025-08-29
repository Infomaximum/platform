package com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.handler.graphql;

import com.infomaximum.cluster.graphql.executor.struct.GSubscriptionPublisher;
import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.network.packet.IPacket;
import com.infomaximum.network.protocol.PacketHandler;
import com.infomaximum.network.session.Session;
import com.infomaximum.network.session.SessionImpl;
import com.infomaximum.network.struct.RemoteAddress;
import com.infomaximum.network.struct.UpgradeRequest;
import com.infomaximum.platform.component.frontend.engine.network.protocol.GraphQLSubscriber;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.packet.Packet;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.packet.TypePacket;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqltransportws.subscriber.WebSocketGraphQLTransportWSSubscriber;
import com.infomaximum.platform.component.frontend.engine.provider.ProviderGraphQLRequestExecuteService;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GExecutionStatistics;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.component.frontend.request.GRequestWebSocket;
import com.infomaximum.platform.component.frontend.utils.GRequestUtils;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GraphQLTransportWSHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(GraphQLTransportWSHandler.class);

    private final ProviderGraphQLRequestExecuteService providerGraphQLRequestExecuteService;

    private final GraphQLSubscriber graphQLSubscriber;

    public GraphQLTransportWSHandler(ProviderGraphQLRequestExecuteService providerGraphQLRequestExecuteService) {
        this.providerGraphQLRequestExecuteService = providerGraphQLRequestExecuteService;
        this.graphQLSubscriber = new GraphQLSubscriber();
    }

    @Override
    public CompletableFuture<IPacket[]> exec(Session session, IPacket packet) {
        Packet requestPacket = (Packet) packet;
        TypePacket typePacket = requestPacket.type;

        if (typePacket == TypePacket.GQL_PING) {
            return CompletableFuture.completedFuture(new IPacket[]{ new Packet(TypePacket.GQL_PONG) });
        } else if (typePacket == TypePacket.GQL_SUBSCRIBE) {
            return execGraphQL(session, requestPacket);
        } else if (typePacket == TypePacket.GQL_COMPLETE) {
            graphQLSubscriber.unSubscriber(session, requestPacket.id);
            return CompletableFuture.completedFuture(new IPacket[0]);
        } else {
            return CompletableFuture.completedFuture(new IPacket[]{
                    new Packet(requestPacket.id, TypePacket.GQL_ERROR)
            });
        }
    }

    private CompletableFuture<IPacket[]> execGraphQL(Session session, Packet requestPacket) {
        JSONObject payload = (JSONObject) requestPacket.payload;

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

        String operationName = payload.getAsString("operation_name");

        UpgradeRequest upgradeRequest = ((SessionImpl) session).getTransportSession().getUpgradeRequest();

        Map<String, String> parameters = upgradeRequest.getParameters();

        RemoteAddress remoteAddress = ((SessionImpl) session).getTransportSession().buildRemoteAddress();

        String parentXTraceId = ((SessionImpl) session).getTransportSession().getXTraceId();
        String xTraceId = parentXTraceId + "." + requestPacket.id;

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

        log.debug("Request {}, xTraceId: {}, remote address: {}, query: {}",
                GRequestUtils.getTraceRequest(gRequest),
                gRequest.getXTraceId(),
                gRequest.getRemoteAddress().endRemoteAddress,
                gRequest.getQuery().replaceAll("[\\s\\t\\r\\n]+", " ")
        );

        return providerGraphQLRequestExecuteService.getGraphQLRequestExecuteService()
                .execute(gRequest)
                .thenCompose(graphQLResponse -> buildResponsePacket(graphQLResponse, gRequest, session, requestPacket));
    }

    private CompletableFuture<IPacket[]> buildResponsePacket(
            GraphQLResponse graphQLResponse, GRequest gRequest,
            Session session, Packet requestPacket
    ) {
        GExecutionStatistics statistics = graphQLResponse.statistics;
        if (statistics == null) {
            log.debug("Request {}, response: {}",
                    (gRequest != null) ? GRequestUtils.getTraceRequest(gRequest) : null,
                    (graphQLResponse.error) ? "error " + graphQLResponse.data.toString() : "success"
            );
        } else {
            log.debug("Request {}, auth: {}, priority: {}, wait: {}, exec: {} ({}), response: {}{}",
                    (gRequest != null) ? GRequestUtils.getTraceRequest(gRequest) : null,
                    statistics.authContext(),
                    statistics.priority(),
                    statistics.timeWait(),
                    statistics.timeExec(),
                    statistics.timeAuth(),
                    (graphQLResponse.error) ? "error " + graphQLResponse.data.toString() : "success",
                    (statistics.accessDenied() != null)?", access_denied: [ " + statistics.accessDenied() + "]": ""
            );
        }

        Object data = graphQLResponse.data;
        if (graphQLResponse.error) {
            JSONArray jErrors = new JSONArray();
            jErrors.add(data);
            return CompletableFuture.completedFuture(new IPacket[]{
                    new Packet(requestPacket.id, TypePacket.GQL_ERROR, jErrors)
            });
        } else {
            if (data instanceof JSONObject jPayload) {
                return CompletableFuture.completedFuture(new IPacket[]{
                        new Packet(requestPacket.id, TypePacket.GQL_NEXT, jPayload),
                        new Packet(requestPacket.id, TypePacket.GQL_COMPLETE)
                });
            } else if (data instanceof GSubscriptionPublisher subscriptionPublisher) {
                WebSocketGraphQLTransportWSSubscriber websocketSubscriber = new WebSocketGraphQLTransportWSSubscriber(graphQLSubscriber, requestPacket.id, ((SessionImpl) session).getTransportSession());
                subscriptionPublisher.subscribe(websocketSubscriber);

                CompletableFuture<IPacket[]> result = new CompletableFuture<>();
                websocketSubscriber.getFirstResponseCompletableFuture()
                        .thenApply(iPacket -> result.complete(new IPacket[]{ iPacket }));
                return result;
            } else {
                throw new RuntimeException("Not support type out: " + data);
            }
        }
    }
}
