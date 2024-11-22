package com.infomaximum.platform.prometheus.metric.base;

import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

public class HistogramMetric implements PrometheusMetric {

    private final Histogram histogram;

    HistogramMetric(Histogram histogram) {
        this.histogram = histogram;
    }

    @Override
    public void register() {
        PrometheusRegistry.defaultRegistry.register(histogram);
    }

    @Override
    public String getName() {
        return histogram.getPrometheusName();
    }

    public void observe(double amount) {
        histogram.observe(amount);
    }

    public void observeWithLabelValues(double amount, String... labelValues) {
        histogram.labelValues(labelValues).observe(amount);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements PrometheusMetric.Builder {

        private final Histogram.Builder builder;

        Builder() {
            builder = Histogram.builder();
        }

        @Override
        public Builder withName(String name) {
            builder.name(name);
            return this;
        }

        @Override
        public Builder withHelp(String help) {
            builder.help(help);
            return this;
        }

        @Override
        public Builder withLabels(String... labelNames) {
            builder.labelNames(labelNames);
            return this;
        }

        @Override
        public Builder withUnit(Unit unit) {
            builder.unit(unit.getUnit());
            return this;
        }

        @Override
        public HistogramMetric build() {
            return new HistogramMetric(builder.build());
        }
    }
}
