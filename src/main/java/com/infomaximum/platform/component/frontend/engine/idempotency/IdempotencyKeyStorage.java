package com.infomaximum.platform.component.frontend.engine.idempotency;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.utils.TimedCache;

import java.time.Duration;

public class IdempotencyKeyStorage extends TimedCache<String, IdempotencyResponse> {

    private final Component component;

    public IdempotencyKeyStorage(Component component) {
        super(Duration.ofMinutes(15).toMillis());
        this.component = component;
    }

    public IdempotencyResponse getFromRemote(String idempotencyKey) throws PlatformException {
        for (var controller : component.getRemotes().getControllers(RControllerIdempotencyKeyStorage.class)) {
            IdempotencyResponse idempotencyResponse = controller.getIdempotencyResponse(idempotencyKey);
            if (idempotencyResponse != null) {
                return idempotencyResponse;
            }
        }
        return null;
    }

    public IdempotencyResponse get(String idempotencyKey, Integer xRetryCount) throws PlatformException {
        if (xRetryCount != null && xRetryCount > 0) {
            return getFromRemote(idempotencyKey);
        }
        return get(idempotencyKey);
    }
}
