package com.infomaximum.platform.component.frontend.engine.filter;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.platform.exception.PlatformException;

public interface FilterGRequest {

    void filter(GRequest request) throws PlatformException;

}
