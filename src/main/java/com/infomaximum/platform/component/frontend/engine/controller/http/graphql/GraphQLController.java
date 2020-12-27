package com.infomaximum.platform.component.frontend.engine.controller.http.graphql;

import com.google.common.net.UrlEscapers;
import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.cluster.graphql.subscription.SingleSubscriber;
import com.infomaximum.platform.component.frontend.engine.FrontendEngine;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteService;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLResponse;
import com.infomaximum.platform.component.frontend.engine.service.requestcomplete.RequestCompleteCallbackService;
import com.infomaximum.platform.component.frontend.engine.service.statistic.StatisticService;
import com.infomaximum.platform.component.frontend.request.graphql.GraphQLRequest;
import com.infomaximum.platform.component.frontend.utils.GRequestUtils;
import com.infomaximum.platform.component.frontend.utils.MimeTypeUtils;
import com.infomaximum.platform.sdk.graphql.out.GOutputFile;
import com.infomaximum.subsystems.exception.GraphQLWrapperSubsystemException;
import com.infomaximum.subsystems.exception.SubsystemException;
import graphql.execution.reactive.CompletionStageMappingPublisher;
import net.minidev.json.JSONObject;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

public class GraphQLController {

    private final static Logger log = LoggerFactory.getLogger(GraphQLController.class);

    private final FrontendEngine frontendEngine;

    public GraphQLController(FrontendEngine frontendEngine) {
        this.frontendEngine = frontendEngine;
    }

    public CompletableFuture<ResponseEntity> execute(HttpServletRequest request) {
        GraphQLRequest graphQLRequest;
        try {
            graphQLRequest = frontendEngine.getGraphQLRequestBuilder().build(request);
        } catch (SubsystemException e) {
            GraphQLWrapperSubsystemException graphQLWrapperSubsystemException = GraphQLRequestExecuteService.coercionGraphQLSubsystemException(e);
            return CompletableFuture.completedFuture(buildResponseEntity(null, graphQLWrapperSubsystemException));
        }

        GRequest gRequest = graphQLRequest.getGRequest();

        log.debug("Request {}, remote address: {}",
                GRequestUtils.getTraceRequest(gRequest),
                gRequest.getRemoteAddress().endRemoteAddress
        );

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
                    } else if (data instanceof CompletionStageMappingPublisher) {
                        CompletionStageMappingPublisher completionPublisher = (CompletionStageMappingPublisher) data;
                        SingleSubscriber singleSubscriber = new SingleSubscriber();
                        completionPublisher.subscribe(singleSubscriber);
                        return singleSubscriber.getCompletableFuture().thenApply(executionResult -> {
                            GraphQLResponse graphQLResponse =
                                    GraphQLRequestExecuteService.buildResponse(executionResult);
                            return buildResponseEntity(gRequest, graphQLResponse);
                        });
                    } else if (data instanceof GOutputFile) {
                        GOutputFile gOutputFile = (GOutputFile) data;
                        Path pathOutputFile = Paths.get(gOutputFile.uri);

                        PathResource pathResource;
                        long fileSize;
                        try {
                            pathResource = new PathResource(pathOutputFile);
                            fileSize = Files.size(pathOutputFile);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        HttpHeaders header = new HttpHeaders();
                        header.add("Content-Disposition", "attachment; filename*=UTF-8''" + UrlEscapers.urlFragmentEscaper().escape(gOutputFile.fileName));
                        header.setContentType(MediaType.valueOf(MimeTypeUtils.findAutoMimeType(gOutputFile.fileName)));
                        header.setContentLength(fileSize);

                        //Помечаем инфу для сервиса сбора статистики
                        request.setAttribute(StatisticService.ATTRIBUTE_DOWNLOAD_FILE_SIZE, fileSize);

                        if (gOutputFile.temp) {
                            //Добавляем callback, что бы после отдачи файла, его удалить
                            request.setAttribute(
                                    RequestCompleteCallbackService.ATTRIBUTE_COMPLETE_REQUEST_CALLBACK,
                                    new RequestCompleteCallbackService.Callback() {
                                        @Override
                                        public void exec(Request request) {
                                            try {
                                                Files.delete(pathOutputFile);
                                            } catch (IOException e) {
                                                log.error("Exception clear temp file", e);//Падать из-за этого не стоит
                                            }
                                        }
                                    }
                            );
                        }

                        return CompletableFuture.completedFuture(
                                new ResponseEntity(pathResource, header, HttpStatus.OK)
                        );
                    } else {
                        throw new RuntimeException("Not support type out: " + out);
                    }
                });
    }

    public ResponseEntity buildResponseEntity(GRequest gRequest, GraphQLWrapperSubsystemException graphQLWrapperSubsystemException) {
        GraphQLRequestExecuteService graphQLRequestExecuteService = frontendEngine.getGraphQLRequestExecuteService();

        GraphQLResponse<JSONObject> graphQLResponse = graphQLRequestExecuteService.buildResponse(graphQLWrapperSubsystemException);
        return buildResponseEntity(gRequest, graphQLResponse);
    }

    private ResponseEntity buildResponseEntity(GRequest gRequest, GraphQLResponse<JSONObject> graphQLResponse) {
        HttpStatus httpStatus;
        JSONObject out = new JSONObject();
        if (!graphQLResponse.error) {
            httpStatus = HttpStatus.OK;
            out.put(GraphQLRequestExecuteService.JSON_PROP_DATA, graphQLResponse.data);
        } else {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            out.put(GraphQLRequestExecuteService.JSON_PROP_ERROR, graphQLResponse.data);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0);

        String sout = out.toString();

        log.debug("Request {}, http code: {}, response: {}",
                (gRequest != null) ? GRequestUtils.getTraceRequest(gRequest) : null,
                httpStatus.value(),
                (graphQLResponse.error) ? sout : "hide"
        );

        return new ResponseEntity(sout.getBytes(StandardCharsets.UTF_8), headers, httpStatus);
    }

}
