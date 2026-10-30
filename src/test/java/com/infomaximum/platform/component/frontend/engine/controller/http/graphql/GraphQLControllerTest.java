package com.infomaximum.platform.component.frontend.engine.controller.http.graphql;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.platform.component.frontend.authcontext.UnauthorizedContext;
import com.infomaximum.platform.component.frontend.engine.FrontendEngine;
import com.infomaximum.platform.component.frontend.engine.download.DownloadStore;
import com.infomaximum.platform.component.frontend.engine.idempotency.IdempotencyKeyStorage;
import com.infomaximum.platform.component.frontend.engine.idempotency.IdempotencyResponse;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteService;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteServiceImp;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GExecutionStatistics;
import com.infomaximum.platform.component.frontend.request.GRequestHttp;
import com.infomaximum.platform.component.frontend.request.graphql.GraphQLRequest;
import com.infomaximum.platform.component.frontend.request.graphql.builder.impl.ClearUploadFilesImpl;
import com.infomaximum.platform.exception.GraphQLWrapperPlatformException;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.querypool.QueryPool;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.state.SystemState;
import com.infomaximum.platform.state.SystemStateSnapshot;
import net.minidev.json.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

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
        when(frontendEngine.getDownloadStore()).thenReturn(new DownloadStore(mock(Component.class)));
        this.graphQLController = new GraphQLController(frontendEngine);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("response", "success");
        responseData = new JSONObject();
        responseData.put(GraphQLRequestExecuteServiceImp.JSON_PROP_DATA, jsonObject);
        doReturn(null).when(idempotencyKeyStorage).getFromRemote(anyString());
        when(frontendEngine.getFilterGRequests()).thenReturn(null);
        when(frontendEngine.getSystemState())
                .thenReturn(new SystemStateSnapshot(SystemState.READY, 0, 0, null));
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

    /**
     * Ошибка с кодом system_not_ready должна отдаваться как HTTP 503 + заголовок Retry-After: 3
     * (а не как 500). Идемпотентный кеш этот ответ не сохраняет, иначе после перехода в READY
     * клиент получил бы прибитый 503-ответ.
     */
    @Test
    public void systemNotReadyReturns503AndSkipsIdempotencyCache() throws ExecutionException, InterruptedException, PlatformException {
        when(frontendEngine.getGraphQLRequestExecuteService()).thenReturn(buildSystemNotReadyServiceStub());

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

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("3");
        // Ответ не сохранён в идемпотентном кеше как READY: остаётся стартовый EXECUTE-маркер.
        IdempotencyResponse cached = idempotencyKeyStorage.get(idempotencyKey, 0);
        assertThat(cached).isNotNull();
        assertThat(cached.state()).isEqualTo(IdempotencyResponse.State.EXECUTE);
    }

    /** Прочие ошибки (не system_not_ready) сохраняют существующее поведение — HTTP 500 без Retry-After. */
    @Test
    public void otherErrorsRemainAt500() throws ExecutionException, InterruptedException {
        when(frontendEngine.getGraphQLRequestExecuteService()).thenReturn(buildAccessDeniedServiceStub());

        var requestHttpBuilder = new GRequestHttp.Builder()
                .withInstantRequest(Instant.now())
                .withRemoteAddress(new GRequest.RemoteAddress("0.0.0.1"))
                .withQuery("{query}")
                .withQueryVariables(new HashMap<>())
                .withOperationName("operation")
                .withXTraceId(UUID.randomUUID().toString())
                .withXRetryCount(0);
        when(frontendEngine.getGraphQLRequestBuilder()).thenReturn(request -> new GraphQLRequest(
                requestHttpBuilder.build(),
                new ClearUploadFilesImpl(null)));

        ResponseEntity responseEntity = graphQLController.execute(null).get();

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNull();
    }

    @Test
    public void skipsBuildAndReturns503WhenSystemNotReady() throws ExecutionException, InterruptedException {
        when(frontendEngine.getSystemState())
                .thenReturn(new SystemStateSnapshot(SystemState.STARTING, 1, 3, null));

        ResponseEntity responseEntity = graphQLController.execute(null).get();

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("3");
        verify(frontendEngine, never()).getGraphQLRequestBuilder();
    }

    /**
     * HEAD не несёт тела: при ошибке операции её тело кладётся в хранилище скачиваний под токен
     * (заголовок X-Download-Token), а GET по этому токену отдаёт тело ошибки — операция повторно
     * не исполняется. Токен одноразовый.
     */
    @Test
    public void headErrorIsAvailableByDownloadToken() throws ExecutionException, InterruptedException {
        when(frontendEngine.getGraphQLRequestExecuteService())
                .thenReturn(buildErrorServiceStub(buildErrorWithParameters(), new AuthorizedStubContext()));
        mockGraphQLRequestBuilder(null);

        ResponseEntity head = graphQLController.execute(mockRequest("HEAD", null)).get();

        assertThat(head.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String token = head.getHeaders().getFirst(GraphQLController.HEADER_DOWNLOAD_TOKEN);
        assertThat(token).isNotBlank();

        ResponseEntity error = graphQLController.execute(mockRequest("GET", token)).get();

        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(error.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat((byte[]) error.getBody()).isEqualTo((byte[]) head.getBody());
        assertThat(new String((byte[]) error.getBody(), StandardCharsets.UTF_8))
                .contains("some_error_code").contains("Отдел № 1");

        ResponseEntity again = graphQLController.execute(mockRequest("GET", token)).get();
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** Для не-HEAD запросов ошибка отдаётся телом, токен не выдаётся. */
    @Test
    public void postErrorHasNoDownloadToken() throws ExecutionException, InterruptedException {
        when(frontendEngine.getGraphQLRequestExecuteService())
                .thenReturn(buildErrorServiceStub(buildErrorWithParameters(), new AuthorizedStubContext()));
        mockGraphQLRequestBuilder(null);

        ResponseEntity post = graphQLController.execute(mockRequest("POST", null)).get();

        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(post.getHeaders().getFirst(GraphQLController.HEADER_DOWNLOAD_TOKEN)).isNull();
    }

    /**
     * Токен ошибки не попадает в идемпотентный кеш: он одноразовый и выдаётся только запросу,
     * прошедшему авторизацию. Ответ, отданный из кеша по Idempotency-Key, токена не содержит.
     */
    @Test
    public void downloadTokenForErrorIsNotStoredInIdempotencyCache() throws ExecutionException, InterruptedException {
        when(frontendEngine.getGraphQLRequestExecuteService())
                .thenReturn(buildErrorServiceStub(buildErrorWithParameters(), new AuthorizedStubContext()));
        mockGraphQLRequestBuilder(UUID.randomUUID().toString());

        ResponseEntity firstHead = graphQLController.execute(mockRequest("HEAD", null)).get();
        ResponseEntity headFromCache = graphQLController.execute(mockRequest("HEAD", null)).get();

        assertThat(firstHead.getHeaders().getFirst(GraphQLController.HEADER_DOWNLOAD_TOKEN)).isNotBlank();
        assertThat(headFromCache.getHeaders().getFirst(GraphQLController.HEADER_DOWNLOAD_TOKEN)).isNull();
    }

    /**
     * Анонимный запрос (без авторизации) токен ошибки не получает: GET по токену отдаёт ошибку
     * без сессии и CSRF, поэтому хранилище наполняется только из авторизованного запроса.
     */
    @Test
    public void unauthorizedHeadErrorHasNoDownloadToken() throws ExecutionException, InterruptedException {
        when(frontendEngine.getGraphQLRequestExecuteService())
                .thenReturn(buildErrorServiceStub(buildErrorWithParameters(), new UnauthorizedContext()));
        mockGraphQLRequestBuilder(null);

        ResponseEntity head = graphQLController.execute(mockRequest("HEAD", null)).get();

        assertThat(head.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(head.getHeaders().getFirst(GraphQLController.HEADER_DOWNLOAD_TOKEN)).isNull();
    }

    /**
     * Ошибки до исполнения операции (система не готова, разбор запроса, фильтры — в т.ч. CSRF и
     * авторизация) в хранилище не кладутся: оно наполняется только из проверенного запроса.
     */
    @Test
    public void errorBeforeOperationHasNoDownloadToken() throws ExecutionException, InterruptedException {
        when(frontendEngine.getSystemState())
                .thenReturn(new SystemStateSnapshot(SystemState.STARTING, 1, 3, null));

        ResponseEntity head = graphQLController.execute(mockRequest("HEAD", null)).get();

        assertThat(head.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(head.getHeaders().getFirst(GraphQLController.HEADER_DOWNLOAD_TOKEN)).isNull();
    }

    private void mockGraphQLRequestBuilder(String idempotencyKey) {
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
    }

    private static HttpServletRequest mockRequest(String method, String downloadToken) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getParameter("downloadToken")).thenReturn(downloadToken);
        return request;
    }

    /** Авторизованный контекст: в subsystems это наследники AuthorizedContext (сотрудник, система). */
    private static class AuthorizedStubContext extends UnauthorizedContext {
    }

    /** Ошибка с вложенными параметрами и не-ASCII строкой. */
    private static JSONObject buildErrorWithParameters() {
        JSONObject item = new JSONObject();
        item.put("id", 1);
        item.put("name", "Отдел № 1");
        JSONObject parameters = new JSONObject();
        parameters.put("cause", "some_cause");
        parameters.put("items", List.of(item));
        JSONObject error = new JSONObject();
        error.put("code", "some_error_code");
        error.put("parameters", parameters);
        return error;
    }

    private static GraphQLRequestExecuteService buildSystemNotReadyServiceStub() {
        return buildErrorServiceStub(GeneralExceptionBuilder.SYSTEM_NOT_READY);
    }

    private static GraphQLRequestExecuteService buildAccessDeniedServiceStub() {
        return buildErrorServiceStub(GeneralExceptionBuilder.ACCESS_DENIED_CODE);
    }

    private static GraphQLRequestExecuteService buildErrorServiceStub(String code) {
        JSONObject error = new JSONObject();
        error.put("code", code);
        return buildErrorServiceStub(error);
    }

    private static GraphQLRequestExecuteService buildErrorServiceStub(JSONObject error) {
        return buildErrorServiceStub(error, null);
    }

    private static GraphQLRequestExecuteService buildErrorServiceStub(JSONObject error, UnauthorizedContext authContext) {
        GExecutionStatistics statistics = authContext == null
                ? null
                : new GExecutionStatistics(authContext, QueryPool.Priority.HIGH, 0, 0, 0, null);
        GraphQLResponse<JSONObject> errorResponse = new GraphQLResponse<>(error, true, statistics);
        return new GraphQLRequestExecuteService() {
            @Override
            public CompletableFuture<GraphQLResponse> execute(GRequest gRequest) {
                return CompletableFuture.completedFuture(errorResponse);
            }

            @Override
            public GraphQLResponse<JSONObject> buildResponse(GraphQLWrapperPlatformException ex) {
                JSONObject e = new JSONObject();
                e.put("code", ex.getPlatformException().getCode());
                return new GraphQLResponse<>(e, true, ex.getStatistics());
            }
        };
    }
}
