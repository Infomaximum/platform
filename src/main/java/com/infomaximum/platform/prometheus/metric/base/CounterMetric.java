package com.infomaximum.platform.prometheus.metric.base;

import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

public class CounterMetric implements PrometheusMetric {

    private final Counter counter;

    CounterMetric(Counter counter) {
        this.counter = counter;
    }

    @Override
    public void register() {
        PrometheusRegistry.defaultRegistry.register(counter);
    }

    @Override
    public String getName() {
        return counter.getPrometheusName();
    }

    public void inc() {
        counter.inc();
    }

    public void inc(long amount) {
        counter.inc(amount);
    }

    public void inc(double amount) {
        counter.inc(amount);
    }

    public void incWithLabelValues(String... labelValues) {
        counter.labelValues(labelValues).inc();
    }

    public void incWithLabelValues(long amount, String... labelValues) {
        counter.labelValues(labelValues).inc(amount);
    }

    public void incWithLabelValues(double amount, String... labelValues) {
        counter.labelValues(labelValues).inc(amount);
    }

    public double get() {
        return counter.get();
    }

    public long getLongValue() {
        return counter.getLongValue();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements PrometheusMetric.Builder {

        private final Counter.Builder builder;

        Builder() {
            builder = Counter.builder();
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
        public CounterMetric build() {
            return new CounterMetric(builder.build());
        }
    }
}
