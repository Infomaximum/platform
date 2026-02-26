package com.infomaximum.platform.component.frontend.engine.controller.http.graphql;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.platform.component.frontend.engine.FrontendEngine;
import com.infomaximum.platform.component.frontend.engine.idempotency.IdempotencyKeyStorage;
import com.infomaximum.platform.component.frontend.engine.idempotency.IdempotencyResponse;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteService;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteServiceImp;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.component.frontend.request.GRequestHttp;
import com.infomaximum.platform.component.frontend.request.graphql.GraphQLRequest;
import com.infomaximum.platform.component.frontend.request.graphql.builder.impl.ClearUploadFilesImpl;
import com.infomaximum.platform.exception.GraphQLWrapperPlatformException;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

public class GraphQLControllerTest {

    private GraphQLController graphQLController;
    private FrontendEngine frontendEngine;
    private IdempotencyKeyStorage idempotencyKeyStorage;
    private JSONObject responseData;

    @BeforeEach
    public void init() throws PlatformException {
        this.frontendEngine = mock(FrontendEngine.class);
        this.idempotencyKeyStorage = spy(new IdempotencyKeyStorage(null));
        when(frontendEngine.getIdempotencyKeyStorage()).thenReturn(idempotencyKeyStorage);
        this.graphQLController = new GraphQLController(frontendEngine);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("response", "success");
        responseData = new JSONObject();
        responseData.put(GraphQLRequestExecuteServiceImp.JSON_PROP_DATA, jsonObject);
        doReturn(null).when(idempotencyKeyStorage).getFromRemote(anyString());
        when(frontendEngine.getFilterGRequests()).thenReturn(null);
        when(frontendEngine.getGraphQLRequestExecuteService()).thenReturn(new GraphQLRequestExecuteService() {
            @Override
            public CompletableFuture<GraphQLResponse> execute(GRequest gRequest) {
                GraphQLResponse<JSONObject> response = new GraphQLResponse<>(jsonObject, false, null);
                return CompletableFuture.completedFuture(response);
            }

            @Override
            public GraphQLResponse<JSONObject> buildResponse(GraphQLWrapperPlatformException graphQLPlatformException) {
                PlatformException e = graphQLPlatformException.getPlatformException();
                JSONObject error = new JSONObject();
                error.put("code", e.getCode());
                return new GraphQLResponse<>(error, true, graphQLPlatformException.getStatistics());
            }
        });
    }

    @Test
    @DisplayName("Выполнение 2-ух одинаковых запросов с одинаковым Idempotency-Key")
    public void executeTest1() throws ExecutionException, InterruptedException, PlatformException {
        var idempotencyKey = UUID.randomUUID().toString();
        var requestHttpBuilder = new GRequestHttp.Builder()
                .withInstantRequest(Instant.now())
                .withRemoteAddress(new GRequest.RemoteAddress("0.0.0.1"))
                .withQuery("{query}")
                .withQueryVariables(new HashMap<>())
                .withOperationName("operation")
                .withXTraceId(UUID.randomUUID().toString())
                .withXRetryCount(0)
                .withIdempotencyKey(idempotencyKey);

        when(frontendEngine.getGraphQLRequestBuilder()).thenReturn(request -> new GraphQLRequest(
                requestHttpBuilder.build(),
                new ClearUploadFilesImpl(null)));

        ResponseEntity responseEntity = graphQLController.execute(null).get();
        Assertions.assertNotNull(responseEntity.getBody());
        Assertions.assertArrayEquals(responseData.toString().getBytes(StandardCharsets.UTF_8), (byte[]) responseEntity.getBody());
        Assertions.assertEquals(1, idempotencyKeyStorage.size());
        IdempotencyResponse idempotencyResponse = idempotencyKeyStorage.get(idempotencyKey, 0);
        Assertions.assertNotNull(idempotencyResponse);
        Assertions.assertTrue(idempotencyResponse.isEqualGRequestHttp(requestHttpBuilder.build()));
        Assertions.assertEquals(IdempotencyResponse.State.READY, idempotencyResponse.state());

        responseEntity = graphQLController.execute(null).get();
        Assertions.assertNotNull(responseEntity.getBody());
        Assertions.assertArrayEquals(responseData.toString().getBytes(StandardCharsets.UTF_8), (byte[]) responseEntity.getBody());
        Assertions.assertEquals(1, idempotencyKeyStorage.size());
        Assertions.assertNotNull(idempotencyKeyStorage.get(idempotencyKey, 0));
        idempotencyResponse = idempotencyKeyStorage.get(idempotencyKey, 0);
        Assertions.assertNotNull(idempotencyResponse);
        Assertions.assertTrue(idempotencyResponse.isEqualGRequestHttp(requestHttpBuilder.build()));
        Assertions.assertEquals(IdempotencyResponse.State.READY, idempotencyResponse.state());
    }

    @Test
    @DisplayName("Выполнение 2-ух одинаковых запросов с разными Idempotency-Key")
    public void executeTest2() throws ExecutionException, InterruptedException {
        var requestHttpBuilder = new GRequestHttp.Builder()
                .withInstantRequest(Instant.now())
                .withRemoteAddress(new GRequest.RemoteAddress("0.0.0.1"))
                .withQuery("{query}")
                .withQueryVariables(new HashMap<>())
                .withOperationName("operation")
                .withXTraceId(UUID.randomUUID().toString())
                .withXRetryCount(0)
                .withIdempotencyKey(UUID.randomUUID().toString());

        when(frontendEngine.getGraphQLRequestBuilder()).thenReturn(request -> new GraphQLRequest(
                requestHttpBuilder.build(),
                new ClearUploadFilesImpl(null)));
        graphQLController.execute(null).get();
        Assertions.assertEquals(1, idempotencyKeyStorage.size());

        when(frontendEngine.getGraphQLRequestBuilder()).thenReturn(request -> new GraphQLRequest(
                requestHttpBuilder
                        .withIdempotencyKey(UUID.randomUUID().toString())
                        .build(),
                new ClearUploadFilesImpl(null)));
        graphQLController.execute(null).get();
        Assertions.assertEquals(2, idempotencyKeyStorage.size());
    }

    @Test
    @DisplayName("Выполнение 2-ух рызных запросов с одинаковым Idempotency-Key. Ошибка idempotency_collision")
    public void executeTest3() throws ExecutionException, InterruptedException {
        var idempotencyKey = UUID.randomUUID().toString();
        var requestHttpBuilder = new GRequestHttp.Builder()
                .withInstantRequest(Instant.now())
                .withRemoteAddress(new GRequest.RemoteAddress("0.0.0.1"))
                .withQuery("{query}")
                .withQueryVariables(new HashMap<>())
                .withOperationName("operation")
                .withXTraceId(idempotencyKey)
                .withXRetryCount(0)
                .withIdempotencyKey(idempotencyKey);

        when(frontendEngine.getGraphQLRequestBuilder()).thenReturn(request -> new GraphQLRequest(
                requestHttpBuilder.build(),
                new ClearUploadFilesImpl(null)));
        graphQLController.execute(null).get();
        Assertions.assertEquals(1, idempotencyKeyStorage.size());

        Assertions.assertEquals(
                GeneralExceptionBuilder.IDEMPOTENCY_COLLISION,
                Assertions.assertThrows(PlatformException.class,
                        () -> graphQLController.getResponseFromIdempotencyKeyStorage(
                                requestHttpBuilder
                                        .withOperationName("newOperation")
                                        .withIdempotencyKey(idempotencyKey)
                                        .withXRetryCount(0)
                                        .build())
                ).getCode());
    }

    @Test
    @DisplayName("Выполнение запроса с X-Retry-Count = 1. " +
            "Эмуляция, если на другой ноде есть сохраненный ответ, но в процессе ожидания нода отваливается.")
    public void executeTest4() throws ExecutionException, InterruptedException, PlatformException {
        var idempotencyKey = UUID.randomUUID().toString();
        var requestHttpBuilder = new GRequestHttp.Builder()
                .withInstantRequest(Instant.now())
                .withRemoteAddress(new GRequest.RemoteAddress("0.0.0.1"))
                .withQuery("{query}")
                .withQueryVariables(new HashMap<>())
                .withOperationName("operation")
                .withXTraceId(idempotencyKey)
                .withXRetryCount(1)
                .withIdempotencyKey(idempotencyKey);

        var executeIdempotencyResponse = new IdempotencyResponse(requestHttpBuilder.build(), IdempotencyResponse.State.EXECUTE, null, null, null);
        when(idempotencyKeyStorage.getFromRemote(idempotencyKey)).thenReturn(
                executeIdempotencyResponse, executeIdempotencyResponse, executeIdempotencyResponse, null);
        when(frontendEngine.getGraphQLRequestBuilder()).thenReturn(request -> new GraphQLRequest(
                requestHttpBuilder.build(),
                new ClearUploadFilesImpl(null)));
        graphQLController.execute(null).get();

        Assertions.assertEquals(1, idempotencyKeyStorage.size());
    }
}
