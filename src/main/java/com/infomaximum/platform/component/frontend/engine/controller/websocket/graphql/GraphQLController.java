package com.infomaximum.platform.component.frontend.engine.controller.websocket.graphql;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.network.mvc.ResponseEntity;
import com.infomaximum.network.protocol.standard.packet.RequestPacket;
import com.infomaximum.network.protocol.standard.packet.ResponsePacket;
import com.infomaximum.network.protocol.standard.packet.TargetPacket;
import com.infomaximum.network.protocol.standard.session.StandardTransportSession;
import com.infomaximum.network.struct.RemoteAddress;
import com.infomaximum.platform.component.frontend.engine.FrontendEngine;
import com.infomaximum.platform.component.frontend.engine.network.protocol.standard.subscriber.WebSocketStandardSubscriber;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteService;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.component.frontend.request.GRequestWebSocket;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.subsystems.exception.GraphQLWrapperSubsystemException;
import graphql.execution.reactive.CompletionStageMappingPublisher;
import net.minidev.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GraphQLController {

    private final static Logger log = LoggerFactory.getLogger(GraphQLController.class);

    private final FrontendEngine frontendEngine;

    public GraphQLController(FrontendEngine frontendEngine) {
        this.frontendEngine = frontendEngine;
    }

    public CompletableFuture<ResponseEntity> exec(StandardTransportSession transportSession, TargetPacket targetPacket) {
        GraphQLRequestExecuteService graphQLRequestExecuteService = frontendEngine.getGraphQLRequestExecuteService();

        JSONObject data = targetPacket.getData();

        if (data == null) {
            return CompletableFuture.completedFuture(ResponseEntity.error(HttpStatus.BAD_REQUEST.value()));
        }
        String query = data.getAsString("query");
        if (query == null || query.trim().isEmpty()) {
            GraphQLWrapperSubsystemException graphQLWrapperSubsystemException = new GraphQLWrapperSubsystemException(
                    GeneralExceptionBuilder.buildEmptyValueException("query")
            );
            JSONObject out = graphQLRequestExecuteService.buildResponse(graphQLWrapperSubsystemException).data;
            return CompletableFuture.completedFuture(ResponseEntity.error(out));
        }

        HashMap<String, Serializable> queryVariables;
        if (data.containsKey("variables")) {
            queryVariables = new HashMap<>((Map<String, Serializable>) data.get("variables"));
        } else {
            queryVariables = new HashMap<>();
        }

        Map<String, String> parameters = new HashMap<>();

        RemoteAddress remoteAddress = transportSession.buildRemoteAddress();
        GRequestWebSocket gRequest = new GRequestWebSocket(
                Instant.now(),
                new GRequest.RemoteAddress(remoteAddress.getRawRemoteAddress(), remoteAddress.getEndRemoteAddress()),
                query, queryVariables,
                transportSession.getSession().getUuid(),
                parameters,
                null
        );

        return frontendEngine.getGraphQLRequestExecuteService()
                .execute(gRequest)
                .thenCompose(graphQLResponse -> buildResponseEntity(graphQLResponse, transportSession, targetPacket));
    }

    private static CompletableFuture<ResponseEntity> buildResponseEntity(
            GraphQLResponse graphQLResponse,
            StandardTransportSession transportSession, TargetPacket packet
    ) {
        if (graphQLResponse.error) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.error((JSONObject) graphQLResponse.data)
            );
        } else {
            Object data = graphQLResponse.data;
            if (data instanceof JSONObject) {
                return CompletableFuture.completedFuture(
                        ResponseEntity.success((JSONObject) graphQLResponse.data)
                );
            } else if (data instanceof CompletionStageMappingPublisher) {
                CompletionStageMappingPublisher completionPublisher = (CompletionStageMappingPublisher) data;
                WebSocketStandardSubscriber websocketSubscriber = new WebSocketStandardSubscriber(transportSession, (RequestPacket) packet);
                completionPublisher.subscribe(websocketSubscriber);
                return websocketSubscriber.getFirstResponseCompletableFuture()
                        .thenApply(iPacket -> convert((ResponsePacket) iPacket));
            } else {
                throw new RuntimeException("Not support type out: " + data);
            }
        }
    }

    private static ResponseEntity convert(ResponsePacket responsePacket) {
        if (responsePacket.getCode() == ResponseEntity.RESPONSE_CODE_OK) {
            return ResponseEntity.success(responsePacket.getData());
        } else {
            return ResponseEntity.error(responsePacket.getData());
        }
    }
}
