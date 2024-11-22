package com.infomaximum.platform.prometheus.metric;

import com.infomaximum.platform.prometheus.metric.base.GaugeMetric;
import com.infomaximum.platform.prometheus.metric.base.PrometheusMetric;

import java.util.concurrent.atomic.AtomicBoolean;

public class FilesystemSizeMetric implements PrometheusMetric {

    public static final String FILESYSTEM_SIZE_METRIC_NAME = "node_filesystem_size_bytes";
    public static final String TYPE = "type";
    public static final String FREE = "Free";
    public static final String USAGE = "Usage";
    public static final String TOTAL = "Total";

    private static final AtomicBoolean registeredWithTheDefaultRegistry = new AtomicBoolean(false);

    public static GaugeMetric filesystemSizeMetric = GaugeMetric.builder()
            .withName(FILESYSTEM_SIZE_METRIC_NAME)
            .withLabels(TYPE)
            .withUnit(Unit.BYTES)
            .withHelp("Filesystem size in bytes.")
            .build();

    @Override
    public void register() {
        if (!registeredWithTheDefaultRegistry.getAndSet(true)) {
            filesystemSizeMetric.register();
        }
    }

    @Override
    public String getName() {
        return FILESYSTEM_SIZE_METRIC_NAME;
    }
}
