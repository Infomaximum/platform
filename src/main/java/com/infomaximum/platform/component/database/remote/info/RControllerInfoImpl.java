package com.infomaximum.platform.component.database.remote.info;

import com.infomaximum.platform.component.database.DatabaseComponent;
import com.infomaximum.platform.component.database.info.DBInfo;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.querypool.AbstractQueryRController;
import com.infomaximum.platform.querypool.ResourceProvider;

import java.util.Optional;

public class RControllerInfoImpl extends AbstractQueryRController<DatabaseComponent> implements RControllerInfo {

    public RControllerInfoImpl(DatabaseComponent component, ResourceProvider resources) {
        super(component, resources);
    }

    @Override
    public String getPlatformGuid() throws PlatformException {
        return Optional.ofNullable(DBInfo.get(component.getRocksDBProvider()))
                .map(DBInfo::getGuid)
                .orElse(null);
    }
}
