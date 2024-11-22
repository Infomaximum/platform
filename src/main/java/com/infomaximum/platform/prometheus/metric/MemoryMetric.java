package com.infomaximum.platform.prometheus.metric;

import com.infomaximum.platform.prometheus.metric.base.GaugeMetric;
import com.infomaximum.platform.prometheus.metric.base.PrometheusMetric;

import java.util.concurrent.atomic.AtomicBoolean;

public class MemoryMetric implements PrometheusMetric {

    public static final String MEMORY_METRIC_NAME = "node_memory_bytes";
    public static final String TYPE = "type";
    public static final String FREE = "Free";
    public static final String USAGE = "Usage";
    public static final String TOTAL = "Total";

    private static final AtomicBoolean registeredWithTheDefaultRegistry = new AtomicBoolean(false);

    public static GaugeMetric memoryMetric = GaugeMetric.builder()
            .withName(MEMORY_METRIC_NAME)
            .withLabels(TYPE)
            .withUnit(Unit.BYTES)
            .withHelp("Process memory in bytes.")
            .build();


    @Override
    public void register() {
        if (!registeredWithTheDefaultRegistry.getAndSet(true)) {
            memoryMetric.register();
        }
    }

    @Override
    public String getName() {
        return MEMORY_METRIC_NAME;
    }
}
