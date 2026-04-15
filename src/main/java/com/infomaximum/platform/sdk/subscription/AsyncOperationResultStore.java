package com.infomaximum.platform.sdk.subscription;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.utils.TimedCache;

import java.io.Serializable;
import java.time.Duration;
import java.util.UUID;

public class AsyncOperationResultStore extends TimedCache<UUID, AsyncOperationResult> {

    public AsyncOperationResultStore() {
        super(Duration.ofMinutes(15).toMillis());
    }

    public UUID register() {
        UUID id = UUID.randomUUID();
        put(id, new AsyncOperationResult.Running());
        return id;
    }

    public void complete(UUID id, Serializable result) {
        put(id, new AsyncOperationResult.Completed(result));
    }

    public void fail(UUID id, PlatformException exception) {
        put(id, new AsyncOperationResult.Failed(exception));
    }
}
