package com.infomaximum.platform.sdk.subscription;

import com.infomaximum.platform.exception.PlatformException;

import java.io.Serializable;

public sealed interface AsyncOperationResult extends Serializable {

    record Running() implements AsyncOperationResult {}

    record Completed(Serializable result) implements AsyncOperationResult {}

    record Failed(PlatformException exception) implements AsyncOperationResult {}
}
