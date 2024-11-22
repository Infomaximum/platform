package com.infomaximum.platform.service.detectresource.resourcemonitor;

import com.infomaximum.platform.exception.PlatformException;

public interface ResourceMonitor {
    ResourceMonitorContext scan() throws PlatformException;
    ResourceMonitorContext getParameters();
}
