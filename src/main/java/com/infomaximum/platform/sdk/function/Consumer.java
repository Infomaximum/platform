package com.infomaximum.platform.sdk.function;

import com.infomaximum.platform.exception.PlatformException;

@FunctionalInterface
public interface Consumer<T> {

    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     */
    void accept(T t) throws PlatformException;
}