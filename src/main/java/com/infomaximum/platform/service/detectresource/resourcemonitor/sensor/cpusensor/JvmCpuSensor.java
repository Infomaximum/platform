package com.infomaximum.platform.service.detectresource.resourcemonitor.sensor.cpusensor;

import com.infomaximum.platform.prometheus.metric.CpuMetric;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class JvmCpuSensor implements CpuSensor{
    private static final Integer intervalValue = 5;
    private OperatingSystemMXBean operatingSystemMXBean;
    private final Duration measuringInterval;

    public JvmCpuSensor() {
        operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        measuringInterval = Duration.ofMillis(intervalValue);
    }

    @Override
    public Double scanCPUActivity() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(measuringInterval.toMillis());
        double cpuValue = Math.max(0, operatingSystemMXBean.getProcessCpuLoad()) * 100D;
        CpuMetric.cpuMetric.set(cpuValue);
        return cpuValue;
    }

    @Override
    public Integer getPhysicalProcessorCount() {
        return operatingSystemMXBean.getAvailableProcessors();
    }
}