package com.infomaximum.platform.prometheus.metric.base;

import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

public class GaugeMetric implements PrometheusMetric {

    private final Gauge gauge;

    GaugeMetric(Gauge gauge) {
        this.gauge = gauge;
    }

    @Override
    public void register() {
        PrometheusRegistry.defaultRegistry.register(gauge);
    }

    @Override
    public String getName() {
        return gauge.getPrometheusName();
    }

    public void inc() {
        gauge.inc();
    }

    public void inc(long amount) {
        gauge.inc(amount);
    }

    public void inc(double amount) {
        gauge.inc(amount);
    }

    public void incWithLabelValues(String... labelValues) {
        gauge.labelValues(labelValues).inc();
    }

    public void incWithLabelValues(long amount, String... labelValues) {
        gauge.labelValues(labelValues).inc(amount);
    }

    public void incWithLabelValues(double amount, String... labelValues) {
        gauge.labelValues(labelValues).inc(amount);
    }

    public void set(double amount) {
        gauge.set(amount);
    }

    public void setWithLabelValues(double amount, String... labelValues) {
        gauge.labelValues(labelValues).set(amount);
    }

    public void dec() {
        gauge.dec();
    }

    public void dec(long amount) {
        gauge.dec(amount);
    }

    public void dec(double amount) {
        gauge.dec(amount);
    }

    public void decWithLabelValues(String... labelValues) {
        gauge.labelValues(labelValues).dec();
    }

    public void decWithLabelValues(long amount, String... labelValues) {
        gauge.labelValues(labelValues).dec(amount);
    }

    public void decWithLabelValues(double amount, String... labelValues) {
        gauge.labelValues(labelValues).dec(amount);
    }

    public double get() {
        return gauge.get();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements PrometheusMetric.Builder {

        private final Gauge.Builder builder;

        Builder() {
            builder = Gauge.builder();
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
        public GaugeMetric build() {
            return new GaugeMetric(builder.build());
        }
    }
}
