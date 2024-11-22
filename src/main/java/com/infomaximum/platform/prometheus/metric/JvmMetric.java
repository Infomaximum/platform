package com.infomaximum.platform.prometheus.metric;

import com.infomaximum.platform.prometheus.metric.base.PrometheusMetric;
import io.prometheus.metrics.instrumentation.jvm.JvmMetrics;

public class JvmMetric implements PrometheusMetric {

    private static final String JVM_METRIC_NAME = "jvm_metric";

    private final JvmMetrics.Builder jvmMetricBuilder;

    public JvmMetric() {
        this.jvmMetricBuilder = JvmMetrics.builder();
    }

    @Override
    public void register() {
        jvmMetricBuilder.register();
    }

    @Override
    public String getName() {
        return JVM_METRIC_NAME;
    }
}
