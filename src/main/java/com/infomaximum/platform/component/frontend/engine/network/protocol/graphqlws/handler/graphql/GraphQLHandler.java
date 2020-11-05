package com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.handler.graphql;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.network.packet.IPacket;
import com.infomaximum.network.protocol.standard.session.StandardTransportSession;
import com.infomaximum.network.session.Session;
import com.infomaximum.platform.component.frontend.context.ContextTransactionRequest;
import com.infomaximum.platform.component.frontend.context.impl.ContextTransactionRequestImpl;
import com.infomaximum.platform.component.frontend.context.source.SourceGRequestAuth;
import com.infomaximum.platform.component.frontend.context.source.impl.SourceGRequestAuthImpl;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.handler.PacketHandler;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.packet.Packet;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.packet.TypePacket;
import com.infomaximum.platform.component.frontend.engine.network.protocol.graphqlws.subscriber.WebSocketGraphQLWSSubscriber;
import com.infomaximum.platform.component.frontend.engine.provider.ProviderGraphQLRequestExecuteService;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLResponse;
import com.infomaximum.platform.component.frontend.request.GRequestWebSocket;
import com.infomaximum.platform.sdk.utils.StreamUtils;
import graphql.ExecutionInput;
import graphql.execution.ExecutionId;
import graphql.execution.reactive.CompletionStageMappingPublisher;
import net.minidev.json.JSONObject;
import org.eclipse.jetty.websocket.api.UpgradeRequest;

import javax.servlet.http.Cookie;
import java.io.Serializable;
import java.net.HttpCookie;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GraphQLHandler implements PacketHandler {

    private final ProviderGraphQLRequestExecuteService providerGraphQLRequestExecuteService;

    public GraphQLHandler(ProviderGraphQLRequestExecuteService providerGraphQLRequestExecuteService) {
        this.providerGraphQLRequestExecuteService = providerGraphQLRequestExecuteService;
    }

    @Override
    public CompletableFuture<IPacket> exec(Session session, Packet packet) {
        TypePacket typePacket = packet.type;

        if (typePacket == TypePacket.GQL_START) {
            return execGraphQL(session, packet);
        } else {
            return CompletableFuture.completedFuture(
                    new Packet(packet.id, TypePacket.GQL_ERROR)
            );
        }
    }

    private CompletableFuture<IPacket> execGraphQL(Session session, Packet requestPacket) {
        JSONObject payload = requestPacket.payload;

        String query = payload.getAsString("query");
        if (query == null || query.trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                    new Packet(requestPacket.id, TypePacket.GQL_ERROR)
            );
        }

        HashMap<String, Serializable> variables;
        JSONObject jVariables = (JSONObject) payload.get("variables");
        if (jVariables != null) {
            variables = new HashMap<>((Map) jVariables);
        } else {
            variables = new HashMap<>();
        }

        UpgradeRequest upgradeRequest = session.getTransportSession().getUpgradeRequest();

        Map<String, String> parameters = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : upgradeRequest.getParameterMap().entrySet()) {
            parameters.put(entry.getKey(), entry.getValue().get(0));
        }

        StandardTransportSession.RemoteAddress remoteAddress = session.getTransportSession().getRemoteAddress();
        GRequestWebSocket gRequest = new GRequestWebSocket(
                Instant.now(),
                new GRequest.RemoteAddress(remoteAddress.getRawRemoteAddress(), remoteAddress.getEndRemoteAddress()),
                query, variables,
                session.uuid,
                parameters,
                buildCookies(upgradeRequest)
        );

        SourceGRequestAuth source = new SourceGRequestAuthImpl(gRequest);
        ContextTransactionRequest context = new ContextTransactionRequestImpl(source);

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .executionId(ExecutionId.generate())
                .query(query)
                .context(context)
                .variables(Collections.unmodifiableMap(variables))
                .build();

        return providerGraphQLRequestExecuteService.getGraphQLRequestExecuteService()
                .execute(gRequest, executionInput)
                .thenCompose(graphQLResponse -> buildResponsePacket(graphQLResponse, session, requestPacket));
    }

    private static CompletableFuture<IPacket> buildResponsePacket(
            GraphQLResponse graphQLResponse,
            Session session, Packet requestPacket
    ) {
        if (graphQLResponse.error) {
            JSONObject jPayload = new JSONObject();
            jPayload.put("errors", graphQLResponse.data);
            return CompletableFuture.completedFuture(
                    new Packet(requestPacket.id, TypePacket.GQL_ERROR, jPayload)
            );
        } else {
            Object data = graphQLResponse.data;
            if (data instanceof JSONObject) {
                JSONObject jPayload = new JSONObject();
                jPayload.put("data", graphQLResponse.data);
                return CompletableFuture.completedFuture(
                        new Packet(requestPacket.id, TypePacket.GQL_DATA, jPayload)
                );
            } else if (data instanceof CompletionStageMappingPublisher) {
                CompletionStageMappingPublisher completionPublisher = (CompletionStageMappingPublisher) data;
                WebSocketGraphQLWSSubscriber websocketSubscriber = new WebSocketGraphQLWSSubscriber(requestPacket.id, session.getTransportSession());
                completionPublisher.subscribe(websocketSubscriber);
                return websocketSubscriber.getFirstResponseCompletableFuture();
            } else {
                throw new RuntimeException("Not support type out: " + data);
            }
        }
    }

    private static Cookie[] buildCookies(UpgradeRequest upgradeRequest) {
        List<HttpCookie> cookies = upgradeRequest.getCookies();
        if (cookies == null) {
            return new Cookie[0];
        } else {
            return upgradeRequest.getCookies().stream()
                    .filter(StreamUtils.distinctByKey(HttpCookie::getName))
                    .map(httpCookie -> new Cookie(httpCookie.getName(), httpCookie.getValue()))
                    .toArray(Cookie[]::new);
        }
    }
}