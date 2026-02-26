package com.infomaximum.platform.component.frontend.engine.idempotency;

import com.infomaximum.cluster.core.remote.struct.RController;
import com.infomaximum.platform.exception.PlatformException;

public interface RControllerIdempotencyKeyStorage extends RController {

    IdempotencyResponse getIdempotencyResponse(String idempotencyKey) throws PlatformException;
}
