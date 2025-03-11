package com.infomaximum.platform.component.frontend.engine;

import com.infomaximum.cluster.core.remote.controller.clusterfile.RControllerClusterFileImpl;
import com.infomaximum.cluster.core.service.transport.executor.ComponentExecutorTransportImpl;
import com.infomaximum.cluster.graphql.GraphQLEngine;
import com.infomaximum.cluster.graphql.executor.subscription.GraphQLSubscribeEngine;
import com.infomaximum.network.Network;
import com.infomaximum.network.builder.BuilderNetwork;
import com.infomaximum.network.builder.BuilderTransport;
import com.infomaximum.network.exception.NetworkException;
import com.infomaximum.network.transport.http.builder.HttpBuilderTransport;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.component.frontend.engine.authorize.RequestAuthorize;
import com.infomaximum.platform.component.frontend.engine.controller.Controllers;
import com.infomaximum.platform.component.frontend.engine.filter.FilterGRequest;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteServiceDisable;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteService;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.GraphQLRequestExecuteServiceImp;
import com.infomaximum.platform.component.frontend.engine.service.requestcomplete.RequestCompleteCallbackService;
import com.infomaximum.platform.component.frontend.engine.service.statistic.StatisticService;
import com.infomaximum.platform.component.frontend.engine.service.statistic.StatisticServiceImpl;
import com.infomaximum.platform.component.frontend.engine.uploadfile.FrontendMultipartSource;
import com.infomaximum.platform.component.frontend.request.graphql.builder.GraphQLRequestBuilder;
import com.infomaximum.platform.component.frontend.request.graphql.builder.impl.DefaultGraphQLRequestBuilder;
import com.infomaximum.platform.prometheus.PrometheusMetricRegistry;
import com.infomaximum.platform.sdk.component.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FrontendEngine implements AutoCloseable {

    private final Builder builder;

    private final Platform platform;
    private final Component component;

    private final GraphQLEngine graphQLEngine;
    private final GraphQLSubscribeEngine graphQLSubscribeEngine;

    private final RequestAuthorize.Builder requestAuthorizeBuilder;
    private final FrontendMultipartSource frontendMiltipartSource;

    private final GraphQLRequestBuilder graphQLRequestBuilder;

    private GraphQLRequestExecuteService graphQLRequestExecuteService;

    private List<FilterGRequest> filterGRequests;

    private Network network;

    private final StatisticService statisticService;
    private final RequestCompleteCallbackService requestCompleteCallbackService;

    private final Controllers controllers;
    private final boolean isGraphQLDisabled;

    private FrontendEngine(Builder builder) {
        this.builder = builder;

        this.platform = builder.platform;
        this.component = builder.component;

        this.graphQLEngine = builder.platform.getGraphQLEngine();
        this.graphQLSubscribeEngine = graphQLEngine.buildSubscribeEngine();

        this.filterGRequests = (builder.filterGRequests.isEmpty())? null : Collections.unmodifiableList(builder.filterGRequests);

        this.requestAuthorizeBuilder = builder.requestAuthorizeBuilder;

        this.frontendMiltipartSource = new FrontendMultipartSource(builder.component);

        this.graphQLRequestBuilder = builder.graphQLRequestBuilder.build(frontendMiltipartSource);

        this.statisticService = builder.statisticService;
        this.requestCompleteCallbackService = new RequestCompleteCallbackService(
                builder.builderNetwork.getUncaughtExceptionHandler()
        );

        //Регистрируем подписчиков
        for (BuilderTransport builderTransport: builder.builderNetwork.getBuilderTransports()) {
            if (builderTransport instanceof HttpBuilderTransport) {
                HttpBuilderTransport httpBuilderTransport = (HttpBuilderTransport) builderTransport;

                httpBuilderTransport.addListener(((StatisticServiceImpl) statisticService).getListener());
                httpBuilderTransport.addListener(requestCompleteCallbackService);
                // Регистрируем обработчик http запросов для метрик Prometheus
                if (builder.prometheusMetricRegistry != null &&
                        builder.prometheusMetricRegistry.getHttpRequestListener() != null) {
                    httpBuilderTransport.addListener(builder.prometheusMetricRegistry.getHttpRequestListener());
                }
            } else {
                throw new RuntimeException("Not support builder transport: " + builderTransport);
            }
        }

        // Регистрируем сбор метрик Prometheus
        if (builder.prometheusMetricRegistry != null) {
            builder.prometheusMetricRegistry.register();
        }

        this.controllers = new Controllers(this);
        this.isGraphQLDisabled = builder.isGraphQLDisabled;
        graphQLEngine.setIntrospectionDisabled(builder.isGraphQlIntrospectionDisabled);
    }

    public ComponentExecutorTransportImpl.Builder registerControllers(ComponentExecutorTransportImpl.Builder builder) {
        if (!isGraphQLDisabled) {
            builder.withRemoteController(
                    platform.getGraphQLEngine().buildRemoteControllerGraphQLSubscribe(component, graphQLSubscribeEngine)
            );
        }
        return builder.withRemoteController(
                        new RControllerClusterFileImpl.Builder(component, frontendMiltipartSource).build()//Обработчик ClusterFiles
                );
    }

    public void start() throws NetworkException {
        graphQLRequestExecuteService = isGraphQLDisabled ?
                new GraphQLRequestExecuteServiceDisable() :
                new GraphQLRequestExecuteServiceImp(
                        component,
                        platform.getQueryPool(),
                        graphQLEngine, graphQLSubscribeEngine,
                        requestAuthorizeBuilder,
                        platform.getUncaughtExceptionHandler()
                );

        network = builder.builderNetwork.build();
    }

    public Network getNetwork() {
        return network;
    }

    public FrontendMultipartSource getFrontendMiltipartSource() {
        return frontendMiltipartSource;
    }

    public GraphQLRequestBuilder getGraphQLRequestBuilder() {
        return graphQLRequestBuilder;
    }

    public List<FilterGRequest> getFilterGRequests() {
        return filterGRequests;
    }

    public GraphQLRequestExecuteService getGraphQLRequestExecuteService() {
        return graphQLRequestExecuteService;
    }

    public GraphQLSubscribeEngine getGraphQLSubscribeEngine() {
        return graphQLSubscribeEngine;
    }

    public Controllers getControllers() {
        return controllers;
    }

    public StatisticService getStatisticService() {
        return statisticService;
    }

    @Override
    public void close() {
        if (network != null) {
            network.close();
        }
    }

    public static class Builder {

        private final Platform platform;
        private final Component component;

        private BuilderNetwork builderNetwork;
        private RequestAuthorize.Builder requestAuthorizeBuilder;

        private GraphQLRequestBuilder.Builder graphQLRequestBuilder = new DefaultGraphQLRequestBuilder.Builder();

        private List<FilterGRequest> filterGRequests = new ArrayList<>();

        private PrometheusMetricRegistry prometheusMetricRegistry;

        private StatisticService statisticService = new StatisticServiceImpl();

        private boolean isGraphQLDisabled = false;
        private boolean isGraphQlIntrospectionDisabled = true;

        public Builder(Platform platform, Component component) {
            this.platform = platform;
            this.component = component;
        }

        public Builder withBuilderNetwork(BuilderNetwork builderNetwork) {
            this.builderNetwork = builderNetwork;
            return this;
        }

        public Builder withPrometheusMetricRegistry(PrometheusMetricRegistry prometheusMetricRegistry) {
            this.prometheusMetricRegistry = prometheusMetricRegistry;
            return this;
        }

        public Builder withRequestAuthorizeBuilder(RequestAuthorize.Builder requestAuthorizeBuilder) {
            this.requestAuthorizeBuilder = requestAuthorizeBuilder;
            return this;
        }

        public Builder withGraphQLRequestBuilder(GraphQLRequestBuilder.Builder graphQLRequestBuilder) {
            this.graphQLRequestBuilder = graphQLRequestBuilder;
            return this;
        }

        public Builder withFilterGRequest(FilterGRequest filter) {
            filterGRequests.add(filter);
            return this;
        }

        public Builder withStatisticService(StatisticService statisticService) {
            this.statisticService = statisticService;
            return this;
        }

        public Builder withGraphQLDisabled(boolean isGraphQLDisabled) {
            this.isGraphQLDisabled = isGraphQLDisabled;
            return this;
        }

        public Builder withGraphQLIntrospectionDisabled(boolean isGraphQLIntrospectionDisabled) {
            this.isGraphQlIntrospectionDisabled = isGraphQLIntrospectionDisabled;
            return this;
        }

        public FrontendEngine build() {
            return new FrontendEngine(this);
        }
    }
}
