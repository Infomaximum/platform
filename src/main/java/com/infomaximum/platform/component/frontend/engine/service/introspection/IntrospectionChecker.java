package com.infomaximum.platform.component.frontend.engine.service.introspection;

import com.infomaximum.platform.component.frontend.context.ContextTransactionRequest;

public interface IntrospectionChecker {

    static IntrospectionChecker getDefault() {
        return context -> true;
    }

    boolean isAllowIntrospection(ContextTransactionRequest context);
}
