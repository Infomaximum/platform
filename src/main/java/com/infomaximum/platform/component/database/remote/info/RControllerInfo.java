package com.infomaximum.platform.component.database.remote.info;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.querypool.QueryRemoteController;

public interface RControllerInfo extends QueryRemoteController {

    String getPlatformGuid() throws PlatformException;
}
