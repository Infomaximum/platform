package com.infomaximum.platform.sdk.graphql.datafetcher.listener;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.context.Context;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;

/**
 * Событие исключения GraphQL-резолвера для {@link DataFetcherExceptionListener}.
 * Содержит только платформенные и JDK-типы.
 *
 * @param exception исключение платформы, брошенное резолвером
 * @param context контекст запроса; {@code null} — контекст исполнения недоступен
 * @param fieldPath путь поля в ответе, например {@code /employees[3]/email}
 * @param operationType тип GraphQL-операции запроса
 * @param arguments вычисленные аргументы поля (inline и variables)
 * @param executionScope хранилище со временем жизни одного GraphQL-запроса
 */
public record DataFetcherExceptionEvent(
        PlatformException exception,
        Context<?> context,
        String fieldPath,
        OperationType operationType,
        Map<String, Object> arguments,
        ExecutionScope executionScope) {

    public DataFetcherExceptionEvent(@NonNull PlatformException exception,
                                     @Nullable Context<?> context,
                                     @NonNull String fieldPath,
                                     @NonNull OperationType operationType,
                                     @NonNull Map<String, Object> arguments,
                                     @NonNull ExecutionScope executionScope) {
        this.exception = exception;
        this.context = context;
        this.fieldPath = fieldPath;
        this.operationType = operationType;
        this.arguments = arguments;
        this.executionScope = executionScope;
    }
}
