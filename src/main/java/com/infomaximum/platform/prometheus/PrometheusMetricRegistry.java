package com.infomaximum.platform.prometheus;

import com.infomaximum.network.event.HttpChannelListener;
import com.infomaximum.platform.prometheus.http.PrometheusRequestListener;
import com.infomaximum.platform.prometheus.metric.*;
import com.infomaximum.platform.prometheus.metric.base.PrometheusMetric;

import java.util.ArrayList;
import java.util.List;

public class PrometheusMetricRegistry implements AutoCloseable {

    private final List<PrometheusMetric> prometheusMetrics;
    private PrometheusRequestListener prometheusRequestListener;

    public PrometheusMetricRegistry() {
        this.prometheusMetrics = new ArrayList<>();
    }

    public PrometheusMetricRegistry addDefaultMetrics() {
        HttpServerRequestsMetric httpServerRequestsMetric = new HttpServerRequestsMetric();
        prometheusMetrics.add(new JvmMetric());
        prometheusMetrics.add(new CpuMetric());
        prometheusMetrics.add(new MemoryMetric());
        prometheusMetrics.add(new FilesystemSizeMetric());
        prometheusMetrics.add(httpServerRequestsMetric);
        this.prometheusRequestListener = new PrometheusRequestListener(httpServerRequestsMetric);
        return this;
    }

    public PrometheusMetricRegistry addCustomMetric(PrometheusMetric prometheusMetric) {
        prometheusMetrics.add(prometheusMetric);
        return this;
    }

    public PrometheusMetricRegistry addCustomMetrics(List<PrometheusMetric> prometheusMetrics) {
        this.prometheusMetrics.addAll(prometheusMetrics);
        return this;
    }

    public void register() {
        prometheusMetrics.forEach(PrometheusMetric::register);
    }

    public HttpChannelListener getHttpRequestListener() {
        return prometheusRequestListener;
    }

    @Override
    public void close() {
        if (prometheusRequestListener != null) {
            prometheusRequestListener.close();
        }
    }
}
