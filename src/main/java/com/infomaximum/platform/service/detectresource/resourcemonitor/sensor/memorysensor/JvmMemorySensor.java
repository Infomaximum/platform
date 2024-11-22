package com.infomaximum.platform.service.detectresource.resourcemonitor.sensor.memorysensor;

import com.infomaximum.platform.prometheus.metric.MemoryMetric;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

public class JvmMemorySensor implements MemorySensor{
    public final OperatingSystemMXBean operatingSystemMXBean;

    public JvmMemorySensor() {
        operatingSystemMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    @Override
    public Long getFreeMemory() {
        long freeMemorySize = operatingSystemMXBean.getFreeMemorySize();
        MemoryMetric.memoryMetric.setWithLabelValues(freeMemorySize, MemoryMetric.FREE);
        return freeMemorySize;
    }
    @Override
    public Long getTotalMemory() {
        long totalMemorySize = operatingSystemMXBean.getTotalMemorySize();
        MemoryMetric.memoryMetric.setWithLabelValues(totalMemorySize, MemoryMetric.TOTAL);
        return totalMemorySize;
    }
    @Override
    public Long getUsedMemory() {
        long usedMemory = getTotalMemory() - getFreeMemory();
        MemoryMetric.memoryMetric.setWithLabelValues(usedMemory, MemoryMetric.USAGE);
        return usedMemory;
    }
}