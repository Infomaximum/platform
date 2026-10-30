package com.infomaximum.platform.sdk.graphql.datafetcher;

import com.infomaximum.cluster.graphql.executor.datafetcher.GDataFetcherExceptionHandler;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.exception.runtime.PlatformRuntimeException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.sdk.graphql.datafetcher.listener.DataFetcherExceptionEvent;
import com.infomaximum.platform.sdk.graphql.datafetcher.listener.DataFetcherExceptionListener;
import com.infomaximum.platform.sdk.graphql.datafetcher.listener.ExecutionScope;
import com.infomaximum.platform.sdk.graphql.datafetcher.listener.OperationType;
import graphql.GraphQLContext;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.MergedField;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetchingEnvironment;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Обработчик исключений GraphQL-резолверов.
 * Пишет предупреждение в лог для исключений платформы, кроме отказов доступа и
 * неверных учётных данных. При наличии {@link DataFetcherExceptionListener}
 * передаёт ему событие по каждому {@link PlatformException}; сбой слушателя
 * подавляется и не влияет на ответ клиенту.
 */
public class PlatformDataFetcherExceptionHandler extends GDataFetcherExceptionHandler {

    private final static Logger log = LoggerFactory.getLogger(PlatformDataFetcherExceptionHandler.class);

    private final DataFetcherExceptionListener listener;

    public PlatformDataFetcherExceptionHandler() {
        this(null);
    }

    /**
     * @param listener слушатель исключений резолверов; {@code null} — без уведомлений
     */
    public PlatformDataFetcherExceptionHandler(@Nullable DataFetcherExceptionListener listener) {
        this.listener = listener;
    }

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {
        if (listener != null) {
            try {
                notifyListener(handlerParameters);
            } catch (RuntimeException e) {
                // сбой слушателя не должен подменять исходную ошибку запроса
                log.warn("DataFetcherExceptionListener failed", e);
            }
        }
        return super.handleException(handlerParameters);
    }

    public void handlerException(Throwable exception) {
        if (exception instanceof PlatformRuntimeException platformRuntimeException) {
            PlatformException platformException = platformRuntimeException.getPlatformException();
            String code = platformException.getCode();
            if (!GeneralExceptionBuilder.ACCESS_DENIED_CODE.equals(code) && !GeneralExceptionBuilder.INVALID_CREDENTIALS.equals(code)) {
                log.warn(exception.getMessage(), exception);
            }
        }
    }

    private void notifyListener(DataFetcherExceptionHandlerParameters handlerParameters) {
        if (!(handlerParameters.getException() instanceof PlatformRuntimeException platformRuntimeException)) {
            return;
        }
        DataFetchingEnvironment environment = handlerParameters.getDataFetchingEnvironment();
        listener.onDataFetcherException(new DataFetcherExceptionEvent(
                platformRuntimeException.getPlatformException(),
                environment != null ? environment.getContext() : null,
                resolveFieldPath(environment),
                resolveOperationType(environment),
                resolveArguments(environment),
                buildExecutionScope(environment)
        ));
    }

    private static @NonNull String resolveFieldPath(@Nullable DataFetchingEnvironment environment) {
        if (environment == null || environment.getExecutionStepInfo() == null) {
            return "";
        }
        Deque<String> names = new ArrayDeque<>();
        for (ExecutionStepInfo info = environment.getExecutionStepInfo();
             info != null && info.getPath() != null && !info.getPath().isRootPath();
             info = info.hasParent() ? info.getParent() : null) {
            if (!info.getPath().isNamedSegment()) {
                continue; // индекс элемента списка — имени поля не несёт
            }
            MergedField field = info.getField();
            names.addFirst(field != null ? field.getName() : info.getPath().getSegmentName());
        }
        return "/" + String.join("/", names);
    }

    private static @NonNull Map<String, Object> resolveArguments(@Nullable DataFetchingEnvironment environment) {
        Map<String, Object> arguments = environment != null ? environment.getArguments() : null;
        if (arguments == null) {
            return Map.of();
        }
        // защита от мутации слушателем; Map.copyOf не подходит — значения аргументов бывают null
        return Collections.unmodifiableMap(arguments);
    }

    private static @NonNull OperationType resolveOperationType(@Nullable DataFetchingEnvironment environment) {
        OperationDefinition operationDefinition = environment != null ? environment.getOperationDefinition() : null;
        if (operationDefinition == null) {
            return OperationType.QUERY;
        }
        return switch (operationDefinition.getOperation()) {
            case MUTATION -> OperationType.MUTATION;
            case SUBSCRIPTION -> OperationType.SUBSCRIPTION;
            case QUERY -> OperationType.QUERY;
        };
    }

    private static @NonNull ExecutionScope buildExecutionScope(@Nullable DataFetchingEnvironment environment) {
        GraphQLContext graphQLContext = environment != null
                ? environment.getGraphQlContext()
                : GraphQLContext.newContext().build();
        return new GraphQLContextExecutionScope(graphQLContext);
    }

    private record GraphQLContextExecutionScope(GraphQLContext graphQLContext) implements ExecutionScope {

        @Override
        public <T> T computeIfAbsent(@NonNull Object key, @NonNull Function<Object, T> mapping) {
            return graphQLContext.computeIfAbsent(key, mapping);
        }
    }
}
