package com.infomaximum.platform.prometheus;

import com.infomaximum.platform.prometheus.metric.base.CounterMetric;
import com.infomaximum.platform.prometheus.metric.base.GaugeMetric;
import com.infomaximum.platform.prometheus.metric.base.HistogramMetric;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PrometheusMetricRegistryTest {

    private static final String LABEL_NAME = "test_label";

    private static final CounterMetric COUNTER_METRIC = CounterMetric.builder()
            .withName("counter_test_total")
            .withLabels(LABEL_NAME)
            .build();

    private static final GaugeMetric GAUGE_METRIC = GaugeMetric.builder()
            .withName("gauge_test_bytes")
            .withHelp("Test gauge metric")
            .withLabels(LABEL_NAME)
            .withUnit(com.infomaximum.platform.prometheus.metric.base.PrometheusMetric.Unit.BYTES)
            .build();

    private static final HistogramMetric HISTOGRAM_METRIC = HistogramMetric.builder()
            .withName("histogram_test_seconds")
            .withUnit(com.infomaximum.platform.prometheus.metric.base.PrometheusMetric.Unit.SECONDS)
            .withLabels(LABEL_NAME)
            .build();

    private final Set<String> jvmMetricsName = Set.of(
            "jvm_threads_state",
            "jvm_buffer_pool_used_bytes",
            "jvm_gc_collection_seconds",
            "jvm_memory_pool_used_bytes",
            "jvm_runtime");

    @Test
    @Order(1)
    public void registryWithoutMetricsTest() {
        new PrometheusMetricRegistry()
                .register();
        MetricSnapshots metricSnapshots = PrometheusRegistry.defaultRegistry.scrape();
        Set<String> metricsName = metricSnapshots.stream()
                .map(metricSnapshot -> metricSnapshot.getMetadata().getPrometheusName())
                .collect(Collectors.toSet());
        Assertions.assertNotNull(metricsName);
        Assertions.assertTrue(metricsName.isEmpty());
    }

    @Test
    @Order(2)
    public void registryWithCustomMetricsTest() {
        new PrometheusMetricRegistry()
                .addCustomMetric(COUNTER_METRIC)
                .addCustomMetrics(List.of(GAUGE_METRIC, HISTOGRAM_METRIC))
                .register();

        MetricSnapshots metricSnapshots = PrometheusRegistry.defaultRegistry.scrape();
        Set<String> metricsName = metricSnapshots.stream()
                .map(metricSnapshot -> metricSnapshot.getMetadata().getPrometheusName())
                .collect(Collectors.toSet());

        Assertions.assertNotNull(metricsName);
        Assertions.assertFalse(metricsName.isEmpty());
        Assertions.assertEquals(3, metricsName.size());
        Assertions.assertTrue(metricsName.containsAll(
                Set.of(COUNTER_METRIC.getName(), GAUGE_METRIC.getName(), HISTOGRAM_METRIC.getName())));
    }

    @Test
    @Order(3)
    public void registryWithDefaultMetricsTest() {
        new PrometheusMetricRegistry()
                .addDefaultMetrics()
                .register();
        MetricSnapshots metricSnapshots = PrometheusRegistry.defaultRegistry.scrape();
        Set<String> metricsName = metricSnapshots.stream()
                .map(metricSnapshot -> metricSnapshot.getMetadata().getPrometheusName())
                .collect(Collectors.toSet());
        Assertions.assertNotNull(metricsName);
        Assertions.assertFalse(metricsName.isEmpty());
        Assertions.assertTrue(metricsName.containsAll(jvmMetricsName));
    }

    @Test
    @Order(4)
    public void counterMetricTest() {
        Assertions.assertThrows(IllegalArgumentException.class, COUNTER_METRIC::inc);
        Assertions.assertThrows(IllegalArgumentException.class, () -> COUNTER_METRIC.inc(1L));
        Assertions.assertThrows(IllegalArgumentException.class, () -> COUNTER_METRIC.inc(1D));

        COUNTER_METRIC.incWithLabelValues(LABEL_NAME);
        COUNTER_METRIC.incWithLabelValues(1L, LABEL_NAME);
        COUNTER_METRIC.incWithLabelValues(1D, LABEL_NAME);

        MetricSnapshots metricSnapshots = PrometheusRegistry.defaultRegistry.scrape();
        MetricSnapshot counterSnapshot = metricSnapshots.stream()
                .filter(metricSnapshot -> metricSnapshot.getMetadata().getPrometheusName().equals(COUNTER_METRIC.getName()))
                .findAny()
                .orElse(null);
        Assertions.assertNotNull(counterSnapshot);
        Assertions.assertTrue(counterSnapshot instanceof CounterSnapshot);
        double value = ((CounterSnapshot) counterSnapshot).getDataPoints().get(0).getValue();
        Assertions.assertEquals(3D, value);
        Assertions.assertThrows(IllegalArgumentException.class, COUNTER_METRIC::get);
        Assertions.assertThrows(IllegalArgumentException.class, COUNTER_METRIC::getLongValue);
    }

    @Test
    @Order(5)
    public void gaugeMetricTest() {
        Assertions.assertThrows(IllegalArgumentException.class, GAUGE_METRIC::inc);
        Assertions.assertThrows(IllegalArgumentException.class, GAUGE_METRIC::dec);
        Assertions.assertThrows(IllegalArgumentException.class, () -> GAUGE_METRIC.inc(1L));
        Assertions.assertThrows(IllegalArgumentException.class, () -> GAUGE_METRIC.inc(1D));
        Assertions.assertThrows(IllegalArgumentException.class, () -> GAUGE_METRIC.dec(1L));
        Assertions.assertThrows(IllegalArgumentException.class, () -> GAUGE_METRIC.dec(1D));
        Assertions.assertThrows(IllegalArgumentException.class, () -> GAUGE_METRIC.set(1D));

        GAUGE_METRIC.incWithLabelValues(LABEL_NAME);
        GAUGE_METRIC.incWithLabelValues(1L, LABEL_NAME);
        GAUGE_METRIC.incWithLabelValues(1D, LABEL_NAME);
        GAUGE_METRIC.setWithLabelValues(5D, LABEL_NAME);
        MetricSnapshots metricSnapshots = PrometheusRegistry.defaultRegistry.scrape();
        MetricSnapshot gaugeSnapshot = metricSnapshots.stream()
                .filter(metricSnapshot -> metricSnapshot.getMetadata().getPrometheusName().equals(GAUGE_METRIC.getName()))
                .findAny()
                .orElse(null);
        Assertions.assertNotNull(gaugeSnapshot);
        Assertions.assertTrue(gaugeSnapshot instanceof GaugeSnapshot);
        double value = ((GaugeSnapshot) gaugeSnapshot).getDataPoints().get(0).getValue();
        Assertions.assertEquals(5D, value);

        GAUGE_METRIC.decWithLabelValues(LABEL_NAME);
        GAUGE_METRIC.decWithLabelValues(1L, LABEL_NAME);
        GAUGE_METRIC.decWithLabelValues(1D, LABEL_NAME);
        metricSnapshots = PrometheusRegistry.defaultRegistry.scrape();
        gaugeSnapshot = metricSnapshots.stream()
                .filter(metricSnapshot -> metricSnapshot.getMetadata().getPrometheusName().equals(GAUGE_METRIC.getName()))
                .findAny()
                .orElse(null);
        Assertions.assertNotNull(gaugeSnapshot);
        Assertions.assertTrue(gaugeSnapshot instanceof GaugeSnapshot);
        value = ((GaugeSnapshot) gaugeSnapshot).getDataPoints().get(0).getValue();
        Assertions.assertEquals(2D, value);
        Assertions.assertThrows(IllegalArgumentException.class, GAUGE_METRIC::get);
    }

    @Test
    @Order(6)
    public void histogramMetricTest() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> HISTOGRAM_METRIC.observe(1D));
        HISTOGRAM_METRIC.observeWithLabelValues(1D, LABEL_NAME);
        MetricSnapshots metricSnapshots = PrometheusRegistry.defaultRegistry.scrape();
        MetricSnapshot histogramSnapshot = metricSnapshots.stream()
                .filter(metricSnapshot -> metricSnapshot.getMetadata().getPrometheusName().equals(HISTOGRAM_METRIC.getName()))
                .findAny()
                .orElse(null);
        Assertions.assertNotNull(histogramSnapshot);
        Assertions.assertTrue(histogramSnapshot instanceof HistogramSnapshot);
        long count = ((HistogramSnapshot) histogramSnapshot).getDataPoints().get(0).getCount();
        double sum = ((HistogramSnapshot) histogramSnapshot).getDataPoints().get(0).getSum();
        Assertions.assertEquals(1D, count);
        Assertions.assertEquals(1D, sum);
    }
}
