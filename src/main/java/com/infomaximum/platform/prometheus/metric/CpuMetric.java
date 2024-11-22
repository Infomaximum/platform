package com.infomaximum.platform.prometheus.metric;

import com.infomaximum.platform.prometheus.metric.base.GaugeMetric;
import com.infomaximum.platform.prometheus.metric.base.PrometheusMetric;

import java.util.concurrent.atomic.AtomicBoolean;

public class CpuMetric implements PrometheusMetric {

    private static final String CPU_METRIC_NAME = "process_cpu_usage_percentage";

    private static final AtomicBoolean registeredWithTheDefaultRegistry = new AtomicBoolean(false);

    public static GaugeMetric cpuMetric = GaugeMetric.builder()
            .withName(CPU_METRIC_NAME)
            .withHelp("CPU usage in percentage. CPU load between 0 and 100.")
            .build();

    @Override
    public void register() {
        if (!registeredWithTheDefaultRegistry.getAndSet(true)) {
            cpuMetric.register();
        }
    }

    @Override
    public String getName() {
        return CPU_METRIC_NAME;
    }
}
