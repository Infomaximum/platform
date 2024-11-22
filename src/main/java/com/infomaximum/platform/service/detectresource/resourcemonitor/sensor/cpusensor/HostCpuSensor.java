package com.infomaximum.platform.service.detectresource.resourcemonitor.sensor.cpusensor;

import com.infomaximum.platform.prometheus.metric.CpuMetric;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class HostCpuSensor implements CpuSensor {
    private static final Integer intervalValue = 300;
    private final CentralProcessor processor;
    private final Duration measuringInterval;

    public HostCpuSensor() {
        processor = new SystemInfo().getHardware().getProcessor();
        measuringInterval = Duration.ofMillis(intervalValue);
    }

    @Override
    public Double scanCPUActivity() throws InterruptedException {
        long[] systemCpuLoadTicks = processor.getSystemCpuLoadTicks();
        TimeUnit.MILLISECONDS.sleep(measuringInterval.toMillis());
        double cpuValue = Math.max(0, processor.getSystemCpuLoadBetweenTicks(systemCpuLoadTicks)) * 100D;
        CpuMetric.cpuMetric.set(cpuValue);
        return cpuValue;
    }

    @Override
    public Integer getPhysicalProcessorCount() {
        return processor.getPhysicalProcessorCount();
    }
}