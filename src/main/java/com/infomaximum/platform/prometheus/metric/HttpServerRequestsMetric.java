package com.infomaximum.platform.prometheus.metric;

import com.infomaximum.platform.prometheus.metric.base.HistogramMetric;
import com.infomaximum.platform.prometheus.metric.base.PrometheusMetric;

import java.util.concurrent.atomic.AtomicBoolean;

public class HttpServerRequestsMetric implements PrometheusMetric {

    public static final String HTTP_SERVER_REQUESTS_METRIC_NAME = "http_server_requests";
    public static final String METHOD = "method";
    public static final String STATUS = "status";
    public static final String PATH = "path";

    private static final AtomicBoolean registeredWithTheDefaultRegistry = new AtomicBoolean(false);

    public final HistogramMetric requestsSecondsMetric;

    public HttpServerRequestsMetric() {
        requestsSecondsMetric = HistogramMetric.builder()
                .withName(HTTP_SERVER_REQUESTS_METRIC_NAME + "_seconds")
                .withUnit(Unit.SECONDS)
                .withLabels(METHOD, STATUS, PATH)
                .build();
    }

    @Override
    public void register() {
        if (!registeredWithTheDefaultRegistry.getAndSet(true)) {
            requestsSecondsMetric.register();
        }
    }

    @Override
    public String getName() {
        return HTTP_SERVER_REQUESTS_METRIC_NAME;
    }
}
