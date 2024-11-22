package com.infomaximum.platform.prometheus.metric.base;

public interface PrometheusMetric {

    void register();

    String getName();

    interface Builder {

        Builder withName(String name);
        Builder withHelp(String help);
        Builder withLabels(String... labelNames);
        Builder withUnit(Unit unit);

        PrometheusMetric build();
    }

    enum Unit {

        RATIO(io.prometheus.metrics.model.snapshots.Unit.RATIO),
        SECONDS(io.prometheus.metrics.model.snapshots.Unit.SECONDS),
        BYTES(io.prometheus.metrics.model.snapshots.Unit.BYTES),
        CELSIUS(io.prometheus.metrics.model.snapshots.Unit.CELSIUS),
        JOULES(io.prometheus.metrics.model.snapshots.Unit.JOULES),
        GRAMS(io.prometheus.metrics.model.snapshots.Unit.GRAMS),
        METERS(io.prometheus.metrics.model.snapshots.Unit.METERS),
        VOLTS(io.prometheus.metrics.model.snapshots.Unit.VOLTS),
        AMPERES(io.prometheus.metrics.model.snapshots.Unit.AMPERES);

        private final io.prometheus.metrics.model.snapshots.Unit unit;

        Unit(io.prometheus.metrics.model.snapshots.Unit unit) {
            this.unit = unit;
        }

        io.prometheus.metrics.model.snapshots.Unit getUnit() {
            return unit;
        }
    }
}
