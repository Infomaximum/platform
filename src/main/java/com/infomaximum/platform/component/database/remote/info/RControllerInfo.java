package com.infomaximum.platform.component.database.remote.info;

import com.infomaximum.cluster.core.remote.struct.RController;
import com.infomaximum.platform.exception.PlatformException;

public interface RControllerInfo extends RController {

    String getPlatformGuid() throws PlatformException;
}
