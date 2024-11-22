package com.infomaximum.platform.service.detectresource.resourcemonitor.sensor.memorysensor;

import com.infomaximum.platform.prometheus.metric.MemoryMetric;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

public class HostMemorySensor implements MemorySensor {
    private final GlobalMemory memory;

    public HostMemorySensor() {
        memory = new SystemInfo().getHardware().getMemory();
    }
    @Override
    public Long getFreeMemory() {
        long memoryAvailable = memory.getAvailable();
        MemoryMetric.memoryMetric.setWithLabelValues(memoryAvailable, MemoryMetric.FREE);
        return memoryAvailable;
    }
    @Override
    public Long getTotalMemory() {
        long memoryTotal = memory.getTotal();
        MemoryMetric.memoryMetric.setWithLabelValues(memoryTotal, MemoryMetric.TOTAL);
        return memoryTotal;
    }
    @Override
    public Long getUsedMemory() {
        long usedMemory = memory.getTotal() - memory.getAvailable();
        MemoryMetric.memoryMetric.setWithLabelValues(usedMemory, MemoryMetric.USAGE);
        return usedMemory;
    }
}