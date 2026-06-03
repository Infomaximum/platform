package com.infomaximum.platform.component.database.remote.info;

import com.infomaximum.cluster.core.remote.AbstractRController;
import com.infomaximum.platform.component.database.DatabaseComponent;
import com.infomaximum.platform.component.database.info.DBInfo;
import com.infomaximum.platform.exception.PlatformException;

import java.util.Optional;

public class RControllerInfoImpl extends AbstractRController<DatabaseComponent> implements RControllerInfo {

    public RControllerInfoImpl(DatabaseComponent component) {
        super(component);
    }

    @Override
    public String getPlatformGuid() throws PlatformException {
        return Optional.ofNullable(DBInfo.get(component.getRocksDBProvider()))
                .map(DBInfo::getGuid)
                .orElse(null);
    }
}
