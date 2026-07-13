package com.infomaximum.platform.component.frontend.engine.controller.http.graphql;

import com.infomaximum.platform.component.frontend.engine.idempotency.IdempotencyKeyStorage;
import com.infomaximum.platform.component.frontend.engine.idempotency.IdempotencyResponse;
import com.infomaximum.platform.component.frontend.engine.idempotency.IdempotencyResponse.State;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteServiceImp;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GExecutionStatistics;
import com.infomaximum.cluster.graphql.executor.struct.GSubscriptionPublisher;
import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.cluster.graphql.subscription.SingleSubscriber;
import com.infomaximum.platform.component.frontend.engine.FrontendEngine;
import com.infomaximum.platform.component.frontend.engine.download.DownloadStore;
import com.infomaximum.platform.component.frontend.engine.filter.FilterGRequest;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteService;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.utils.GraphQLExecutionResultUtils;
import com.infomaximum.platform.component.frontend.engine.service.requestcomplete.RequestCompleteCallbackService;
import com.infomaximum.platform.component.frontend.engine.service.statistic.StatisticService;
import com.infomaximum.platform.component.frontend.request.GRequestHttp;
import com.infomaximum.platform.component.frontend.request.graphql.GraphQLRequest;
import com.infomaximum.platform.component.frontend.utils.GRequestUtils;
import com.infomaximum.platform.exception.GraphQLWrapperPlatformException;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.sdk.graphql.out.GOutputFile;
import com.infomaximum.platform.utils.EscapeUtils;
import com.infomaximum.platform.utils.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import net.minidev.json.JSONObject;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GraphQLController {

    private final static Logger log = LoggerFactory.getLogger(GraphQLController.class);

    /** Параметр запроса с токеном скачивания (GET-навигация по токену вместо CSRF-заголовка). */
    private final static String PARAM_DOWNLOAD_TOKEN = "downloadToken";
    /** Заголовок ответа HEAD с токеном подготовленного файла. */
    private final static String HEADER_DOWNLOAD_TOKEN = "X-Download-Token";
    /** Максимальная длина сериализованного GraphQL-ответа в символах. */
    private final static int MAX_RESPONSE_CHARS = Integer.MAX_VALUE / 3;

    private final FrontendEngine frontendEngine;
    private final IdempotencyKeyStorage idempotencyKeyStorage;
    private final DownloadStore downloadStore;

    public GraphQLController(FrontendEngine frontendEngine) {
        this.frontendEngine = frontendEngine;
        this.idempotencyKeyStorage = frontendEngine.getIdempotencyKeyStorage();
        this.downloadStore = frontendEngine.getDownloadStore();
    }

    public CompletableFuture<ResponseEntity> execute(HttpServletRequest request) {
        // Отдача ранее подготовленного файла по токену (GET-навигация). Перехват ДО разбора
        // запроса и цикла CSRF-фильтра: операция повторно не исполняется, авторизует токен.
        if (request != null) {
            String downloadToken = request.getParameter(PARAM_DOWNLOAD_TOKEN);
            if (downloadToken != null) {
                GOutputFile stored = downloadStore.take(downloadToken);
                if (stored == null) {
                    return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
                }
                return CompletableFuture.completedFuture(buildFileResponseEntity(stored, request, null));
            }
        }

        GraphQLRequest graphQLRequest;
        try {
            graphQLRequest = frontendEngine.getGraphQLRequestBuilder().build(request);
        } catch (PlatformException e) {
            GraphQLWrapperPlatformException graphQLWrapperSubsystemException = GraphQLExecutionResultUtils.coercionGraphQLPlatformException(e);
            return CompletableFuture.completedFuture(buildResponseEntity(null, graphQLWrapperSubsystemException));
        }

        GRequest gRequest = graphQLRequest.getGRequest();

        log.debug("Request {}, xTraceId: {}, xRequestId: {}, xRetryCount: {}, idempotencyKey: {}, remote address: {}, query: {}",
                GRequestUtils.getTraceRequest(gRequest),
                gRequest.getXTraceId(),
                gRequest instanceof GRequestHttp gRequestHttp ? gRequestHttp.getXRequestId() : null,
                gRequest instanceof GRequestHttp gRequestHttp ? gRequestHttp.getXRetryCount() : null,
                gRequest instanceof GRequestHttp gRequestHttp ? gRequestHttp.getIdempotencyKey() : null,
                gRequest.getRemoteAddress().endRemoteAddress,
                gRequest.getQuery().replaceAll("[\\s\\t\\r\\n]+", " ")
        );

        if (frontendEngine.getFilterGRequests() != null) {
            try {
                for (FilterGRequest filter : frontendEngine.getFilterGRequests()) {
                    filter.filter(gRequest);
                }
            } catch (PlatformException e) {
                GraphQLWrapperPlatformException graphQLWrapperSubsystemException = GraphQLExecutionResultUtils.coercionGraphQLPlatformException(e);
                return CompletableFuture.completedFuture(buildResponseEntity(null, graphQLWrapperSubsystemException));
            }
        }

        if (gRequest instanceof GRequestHttp gRequestHttp) {
            try {
                ResponseEntity responseEntity = getResponseFromIdempotencyKeyStorage(gRequestHttp);
                if (responseEntity != null) {
                    return CompletableFuture.completedFuture(responseEntity);
                }
            } catch (PlatformException e) {
                GraphQLWrapperPlatformException graphQLWrapperSubsystemException = GraphQLExecutionResultUtils.coercionGraphQLPlatformException(e);
                return CompletableFuture.completedFuture(buildResponseEntity(gRequest, graphQLWrapperSubsystemException));
            }
        }

        return frontendEngine.getGraphQLRequestExecuteService().execute(gRequest)
                .whenComplete((graphQLResponse, throwable) -> {//Встраиваемся в поток, и прокидавыем все(включая ошибки) дальше
                    graphQLRequest.close();//Все чистим
                })
                .thenCompose(out -> {//Возвращаем так же future
                    Object data = out.data;
                    if (data instanceof JSONObject) {
                        return CompletableFuture.completedFuture(
                                buildResponseEntity(gRequest, out)
                        );
                    } else if (data instanceof GSubscriptionPublisher completionPublisher) {
                        SingleSubscriber singleSubscriber = new SingleSubscriber();
                        completionPublisher.subscribe(singleSubscriber);
                        return singleSubscriber.getCompletableFuture().thenApply(executionResult -> {
                            GraphQLResponse<JSONObject> graphQLResponse =
                                    GraphQLExecutionResultUtils.buildResponse(executionResult, null);
                            return buildResponseEntity(gRequest, graphQLResponse);
                        });
                    } else if (data instanceof GOutputFile gOutputFile) {
                        if (isHead(request)) {
                            // Подготовка скачивания: кладём файл в store под токен и отдаём только
                            // метаданные + токен. Тело не пишем (HEAD), temp-файл НЕ удаляем —
                            // он нужен будущему GET по токену (закрывает двойную генерацию).
                            String token = downloadStore.put(gOutputFile);
                            HttpHeaders header = buildFileHeaders(gOutputFile, gRequest);
                            header.add(HEADER_DOWNLOAD_TOKEN, token);
                            return CompletableFuture.completedFuture(
                                    ResponseEntity.ok().headers(header).build()
                            );
                        }
                        return CompletableFuture.completedFuture(
                                buildFileResponseEntity(gOutputFile, request, gRequest)
                        );
                    } else {
                        throw new RuntimeException("Not support type out: " + out);
                    }
                });
    }

    public ResponseEntity buildResponseEntity(GRequest gRequest, GraphQLWrapperPlatformException graphQLWrapperSubsystemException) {
        GraphQLRequestExecuteService graphQLRequestExecuteService = frontendEngine.getGraphQLRequestExecuteService();

        GraphQLResponse<JSONObject> graphQLResponse = graphQLRequestExecuteService.buildResponse(graphQLWrapperSubsystemException);
        return buildResponseEntity(gRequest, graphQLResponse);
    }

    private ResponseEntity buildResponseEntity(GRequest gRequest, GraphQLResponse<JSONObject> graphQLResponse) {
        HttpStatus httpStatus;
        JSONObject out = new JSONObject();
        boolean isSystemNotReady = false;
        if (!graphQLResponse.error) {
            httpStatus = HttpStatus.OK;
            out.put(GraphQLRequestExecuteServiceImp.JSON_PROP_DATA, graphQLResponse.data);
        } else {
            isSystemNotReady = graphQLResponse.data != null
                    && GeneralExceptionBuilder.SYSTEM_NOT_READY.equals(graphQLResponse.data.get("code"));
            httpStatus = isSystemNotReady
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.INTERNAL_SERVER_ERROR;
            out.put(GraphQLRequestExecuteServiceImp.JSON_PROP_ERROR, graphQLResponse.data);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0);
        if (isSystemNotReady) {
            headers.set(HttpHeaders.RETRY_AFTER, "3");
        }
        applyResponseAppendix(headers, gRequest);

        String sout;
        byte[] bout;
        try {
            sout = StringUtils.toLimitedJsonString(out, MAX_RESPONSE_CHARS);
            bout = StringUtils.getBytesUTF8(sout);
        } catch (PlatformException e) {
            log.warn("Request {}, response rejected: too large (>= {} chars)",
                    (gRequest != null) ? GRequestUtils.getTraceRequest(gRequest) : null, MAX_RESPONSE_CHARS);
            GraphQLWrapperPlatformException wrapperPE = GraphQLExecutionResultUtils.coercionGraphQLPlatformException(e);
            return buildResponseEntity(gRequest, wrapperPE);
        }

        // system_not_ready не кешируется по идемпотентному ключу
        if (gRequest instanceof GRequestHttp gRequestHttp && gRequestHttp.getIdempotencyKey() != null && !isSystemNotReady) {
            putResponseToIdempotencyKeyStorage(gRequestHttp, bout, httpStatus, headers);
        }

        GExecutionStatistics statistics = graphQLResponse.statistics;
        if (statistics == null) {
            log.debug("Request {}, response: {} - {}",
                    (gRequest != null) ? GRequestUtils.getTraceRequest(gRequest) : null,
                    httpStatus.value(),
                    (graphQLResponse.error) ? sout : "hide(" + bout.length + " bytes)"
            );
        } else {
            log.debug("Request {}, auth: {}, priority: {}, wait: {}, exec: {} ({}), response: {} - {}{}",
                    (gRequest != null) ? GRequestUtils.getTraceRequest(gRequest) : null,
                    statistics.authContext(),
                    statistics.priority(),
                    statistics.timeWait(),
                    statistics.timeExec(), statistics.timeAuth(),
                    httpStatus.value(),
                    (graphQLResponse.error) ? sout : "hide(" + bout.length + " bytes)",
                    (statistics.accessDenied() != null)?", access_denied: [ " + statistics.accessDenied() + "]": ""
            );
        }

        return new ResponseEntity(bout, headers, httpStatus);
    }

    private static boolean isHead(HttpServletRequest request) {
        return "HEAD".equalsIgnoreCase(request.getMethod());
    }

    /**
     * Собирает заголовки отдачи файла: {@code Content-Disposition: attachment},
     * тип, длину и кэш-политику; плюс перенос слота «намерения ответа».
     *
     * @param gOutputFile отдаваемый файл.
     * @param gRequest    запрос; {@code null} на пути отдачи по токену (слот не переносится).
     * @return заголовки ответа.
     */
    private HttpHeaders buildFileHeaders(GOutputFile gOutputFile, @Nullable GRequest gRequest) {
        HttpHeaders header = new HttpHeaders();
        header.add("Content-Disposition", "attachment; filename*=UTF-8''" + EscapeUtils.escapeFileNameFromContentDisposition(gOutputFile.fileName));
        header.setContentType(MediaType.valueOf(gOutputFile.mimeType.value));
        header.setContentLength(gOutputFile.getSize());
        if (gOutputFile.cache) {
            header.setCacheControl("public, max-age=86400");
        } else {
            header.setCacheControl("no-cache, no-store, must-revalidate");
            header.setPragma("no-cache");
            header.setExpires(0);
        }
        applyResponseAppendix(header, gRequest);
        return header;
    }

    /**
     * Строит ответ с телом файла: заголовки + тело (из памяти либо потоково с диска),
     * учёт размера для статистики и удаление temp-файла после отдачи.
     *
     * @param gOutputFile отдаваемый файл.
     * @param request     HTTP-запрос (для атрибутов статистики и callback'а удаления).
     * @param gRequest    запрос-контекст; {@code null} на пути отдачи по токену.
     * @return ответ {@code 200} с телом файла.
     */
    private ResponseEntity buildFileResponseEntity(GOutputFile gOutputFile, HttpServletRequest request, @Nullable GRequest gRequest) {
        HttpHeaders header = buildFileHeaders(gOutputFile, gRequest);

        //Помечаем инфу для сервиса сбора статистики
        request.setAttribute(StatisticService.ATTRIBUTE_DOWNLOAD_FILE_SIZE, gOutputFile.getSize());

        if (gOutputFile.temp) {
            //Добавляем callback, что бы после отдачи файла, его удалить
            request.setAttribute(
                    RequestCompleteCallbackService.ATTRIBUTE_COMPLETE_REQUEST_CALLBACK,
                    new RequestCompleteCallbackService.Callback() {
                        @Override
                        public void exec(Request request) {
                            try {
                                Files.delete(Paths.get(gOutputFile.uri));
                            } catch (IOException e) {
                                log.error("Exception clear temp file", e);//Падать из-за этого не стоит
                            }
                        }
                    }
            );
        }

        Object body;
        if (gOutputFile.body != null) {
            body = gOutputFile.body;
        } else {
            Path pathOutputFile = Paths.get(gOutputFile.uri);
            body = new PathResource(pathOutputFile);
        }

        return new ResponseEntity(body, header, HttpStatus.OK);
    }

    /**
     * Переносит накопленный обработкой запроса слот «намерения ответа» ({@code Set-Cookie}
     * и дополнительные заголовки из {@link GRequestHttp}) в заголовки HTTP-ответа. Для не-HTTP
     * запросов и пустого слота — ничего не делает.
     *
     * @param headers  заголовки формируемого ответа.
     * @param gRequest запрос; слот читается, только если это {@link GRequestHttp}.
     */
    private static void applyResponseAppendix(@NonNull HttpHeaders headers, @Nullable GRequest gRequest) {
        if (!(gRequest instanceof GRequestHttp gRequestHttp)) {
            return;
        }
        List<String> setCookies = gRequestHttp.getResponseSetCookies();
        if (setCookies != null) {
            for (String setCookie : setCookies) {
                headers.add(HttpHeaders.SET_COOKIE, setCookie);
            }
        }
        Map<String, String> extraHeaders = gRequestHttp.getResponseHeaders();
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::add);
        }
    }

    public ResponseEntity getResponseFromIdempotencyKeyStorage(GRequestHttp gRequestHttp) throws PlatformException {
        String idempotencyKey = gRequestHttp.getIdempotencyKey();
        Integer xRetryCount = gRequestHttp.getXRetryCount();
        if (idempotencyKey == null) {
            return null;
        }
        IdempotencyResponse idempotencyResponse = idempotencyKeyStorage.get(idempotencyKey, xRetryCount);
        if (idempotencyResponse == null) {
            idempotencyKeyStorage.put(idempotencyKey,
                    new IdempotencyResponse(gRequestHttp, State.EXECUTE, null, null, null));
            return null;
        }
        if (!idempotencyResponse.isEqualGRequestHttp(gRequestHttp)) {
            throw GeneralExceptionBuilder.buildIdempotencyCollisionException(idempotencyKey);
        }
        while (idempotencyResponse.state().equals(State.EXECUTE)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return null;
            }
            idempotencyResponse = idempotencyKeyStorage.get(idempotencyKey, xRetryCount);
            if (idempotencyResponse == null) {
                idempotencyKeyStorage.put(idempotencyKey,
                        new IdempotencyResponse(gRequestHttp, State.EXECUTE, null, null, null));
                return null;
            }
        }
        log.debug("Request {}, idempotencyKey: {}, response: {} - {}",
                GRequestUtils.getTraceRequest(gRequestHttp),
                idempotencyKey,
                idempotencyResponse.httpStatus().value(),
                "hide(" + idempotencyResponse.responseData().length + " bytes)"
        );
        return new ResponseEntity(idempotencyResponse.responseData(), idempotencyResponse.headers(), idempotencyResponse.httpStatus());
    }

    public void putResponseToIdempotencyKeyStorage(GRequestHttp gRequestHttp,
                                                   byte[] responseData,
                                                   HttpStatus httpStatus,
                                                   HttpHeaders headers) {
        String idempotencyKey = gRequestHttp.getIdempotencyKey();
        IdempotencyResponse idempotencyResponse = idempotencyKeyStorage.get(idempotencyKey);
        if (idempotencyResponse != null && !idempotencyResponse.isEqualGRequestHttp(gRequestHttp)) {
            return;
        }
        idempotencyKeyStorage.put(idempotencyKey,
                new IdempotencyResponse(gRequestHttp, State.READY, responseData, httpStatus, headers));
    }
}
