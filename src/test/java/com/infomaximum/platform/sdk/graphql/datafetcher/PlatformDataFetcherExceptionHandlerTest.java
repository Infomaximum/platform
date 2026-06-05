package com.infomaximum.platform.sdk.graphql.datafetcher;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.exception.runtime.PlatformRuntimeException;
import com.infomaximum.platform.sdk.context.Context;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.sdk.graphql.datafetcher.listener.DataFetcherExceptionEvent;
import com.infomaximum.platform.sdk.graphql.datafetcher.listener.OperationType;
import graphql.GraphQLContext;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;
import graphql.execution.MergedField;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тестируется передача событий {@code DataFetcherExceptionListener} и сохранение
 * штатного поведения обработчика исключений GraphQL-резолверов.
 */
class PlatformDataFetcherExceptionHandlerTest {

    /**
     * Событие по исключению платформы доходит до слушателя с данными поля:
     * путь, тип операции, аргументы, контекст.
     */
    @Test
    void deliversPlatformExceptionEventToListener() {
        PlatformException accessDenied = GeneralExceptionBuilder.buildAccessDeniedException();
        Context<?> context = mock(Context.class);
        DataFetchingEnvironment environment = environment(context, OperationDefinition.Operation.MUTATION,
                "/employee/update", Map.of("id", 105L));

        AtomicReference<DataFetcherExceptionEvent> captured = new AtomicReference<>();
        PlatformDataFetcherExceptionHandler handler = new PlatformDataFetcherExceptionHandler(captured::set);

        handler.handleException(parameters(new PlatformRuntimeException(accessDenied), environment)).join();

        DataFetcherExceptionEvent event = captured.get();
        assertThat(event).isNotNull();
        assertThat(event.exception()).isSameAs(accessDenied);
        assertThat(event.context()).isSameAs(context);
        assertThat(event.fieldPath()).isEqualTo("/employee/update");
        assertThat(event.operationType()).isEqualTo(OperationType.MUTATION);
        assertThat(event.arguments()).containsEntry("id", 105L);
    }

    /**
     * Путь поля собирается из имён полей, а не из алиасов (response key):
     * запрос {@code mutation { employee { a: update } } } даёт {@code /employee/update}.
     */
    @Test
    void fieldPathUsesFieldNameNotAlias() {
        PlatformException accessDenied = GeneralExceptionBuilder.buildAccessDeniedException();
        ExecutionStepInfo stepInfo = stepInfoChain(List.of("employee", "a"), List.of("employee", "update"));
        DataFetchingEnvironment environment = mock(DataFetchingEnvironment.class);
        when(environment.getExecutionStepInfo()).thenReturn(stepInfo);
        when(environment.getContext()).thenReturn(mock(Context.class));
        when(environment.getArguments()).thenReturn(Map.of("id", 1L));
        when(environment.getOperationDefinition()).thenReturn(
                OperationDefinition.newOperationDefinition().operation(OperationDefinition.Operation.MUTATION).build());
        when(environment.getGraphQlContext()).thenReturn(GraphQLContext.newContext().build());
        when(environment.getMergedField()).thenReturn(
                MergedField.newMergedField(Field.newField("field").build()).build());

        AtomicReference<DataFetcherExceptionEvent> captured = new AtomicReference<>();
        PlatformDataFetcherExceptionHandler handler = new PlatformDataFetcherExceptionHandler(captured::set);

        handler.handleException(parameters(new PlatformRuntimeException(accessDenied), environment)).join();

        assertThat(captured.get().fieldPath()).isEqualTo("/employee/update");
    }

    /**
     * Исключение, не являющееся {@code PlatformRuntimeException}, слушателю не передаётся.
     */
    @Test
    void skipsNonPlatformException() {
        DataFetchingEnvironment environment = environment(null, OperationDefinition.Operation.QUERY,
                "/employees", Map.of());

        AtomicReference<DataFetcherExceptionEvent> captured = new AtomicReference<>();
        PlatformDataFetcherExceptionHandler handler = new PlatformDataFetcherExceptionHandler(captured::set);

        handler.handleException(parameters(new IllegalStateException("boom"), environment)).join();

        assertThat(captured.get()).isNull();
    }

    /**
     * Сбой слушателя подавляется: результат обработки содержит исходную ошибку запроса.
     */
    @Test
    void listenerFailureDoesNotAffectResult() {
        PlatformException accessDenied = GeneralExceptionBuilder.buildAccessDeniedException();
        DataFetchingEnvironment environment = environment(null, OperationDefinition.Operation.QUERY,
                "/employees", Map.of());

        PlatformDataFetcherExceptionHandler handler = new PlatformDataFetcherExceptionHandler(event -> {
            throw new IllegalStateException("listener failed");
        });

        DataFetcherExceptionHandlerResult result =
                handler.handleException(parameters(new PlatformRuntimeException(accessDenied), environment)).join();

        assertThat(result.getErrors()).hasSize(1);
    }

    /**
     * Без слушателя обработчик ведёт себя как прежде: ошибка формируется штатно.
     */
    @Test
    void behavesAsBeforeWithoutListener() {
        DataFetchingEnvironment environment = environment(null, OperationDefinition.Operation.QUERY,
                "/employees", Map.of());

        PlatformDataFetcherExceptionHandler handler = new PlatformDataFetcherExceptionHandler();

        DataFetcherExceptionHandlerResult result =
                handler.handleException(parameters(new IllegalStateException("boom"), environment)).join();

        assertThat(result.getErrors()).hasSize(1);
    }

    /**
     * Тип операции subscription доносится до слушателя без подмены на чтение/запись.
     */
    @Test
    void deliversSubscriptionOperationType() {
        PlatformException accessDenied = GeneralExceptionBuilder.buildAccessDeniedException();
        DataFetchingEnvironment environment = environment(null, OperationDefinition.Operation.SUBSCRIPTION,
                "/employees", Map.of());

        AtomicReference<DataFetcherExceptionEvent> captured = new AtomicReference<>();
        PlatformDataFetcherExceptionHandler handler = new PlatformDataFetcherExceptionHandler(captured::set);

        handler.handleException(parameters(new PlatformRuntimeException(accessDenied), environment)).join();

        assertThat(captured.get().operationType()).isEqualTo(OperationType.SUBSCRIPTION);
    }

    /**
     * Карта аргументов события защищена от мутации слушателем.
     */
    @Test
    void argumentsAreImmutableForListener() {
        PlatformException accessDenied = GeneralExceptionBuilder.buildAccessDeniedException();
        DataFetchingEnvironment environment = environment(null, OperationDefinition.Operation.MUTATION,
                "/employee/update", new HashMap<>(Map.of("id", 105L)));

        AtomicReference<DataFetcherExceptionEvent> captured = new AtomicReference<>();
        PlatformDataFetcherExceptionHandler handler = new PlatformDataFetcherExceptionHandler(captured::set);

        handler.handleException(parameters(new PlatformRuntimeException(accessDenied), environment)).join();

        assertThatThrownBy(() -> captured.get().arguments().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * {@code ExecutionScope} события атомарно сохраняет значение: повторный вызов с тем же
     * ключом возвращает прежний экземпляр (основа дедупликации у подписчиков).
     */
    @Test
    void executionScopeKeepsValuePerKey() {
        PlatformException accessDenied = GeneralExceptionBuilder.buildAccessDeniedException();
        DataFetchingEnvironment environment = environment(null, OperationDefinition.Operation.QUERY,
                "/employees", Map.of());

        AtomicReference<DataFetcherExceptionEvent> captured = new AtomicReference<>();
        PlatformDataFetcherExceptionHandler handler = new PlatformDataFetcherExceptionHandler(captured::set);

        handler.handleException(parameters(new PlatformRuntimeException(accessDenied), environment)).join();

        Object first = captured.get().executionScope().computeIfAbsent("key", k -> new Object());
        Object second = captured.get().executionScope().computeIfAbsent("key", k -> new Object());
        assertThat(second).isSameAs(first);
    }

    private static DataFetcherExceptionHandlerParameters parameters(Throwable exception,
                                                                    DataFetchingEnvironment environment) {
        return DataFetcherExceptionHandlerParameters.newExceptionParameters()
                .exception(exception)
                .dataFetchingEnvironment(environment)
                .build();
    }

    private static DataFetchingEnvironment environment(Context<?> context,
                                                       OperationDefinition.Operation operation,
                                                       String path,
                                                       Map<String, Object> arguments) {
        // путь без алиасов: реальное имя поля каждого сегмента совпадает с сегментом пути
        ExecutionStepInfo stepInfo = stepInfoChain(splitSegments(path), splitSegments(path));
        DataFetchingEnvironment environment = mock(DataFetchingEnvironment.class);
        when(environment.getExecutionStepInfo()).thenReturn(stepInfo);
        when(environment.getContext()).thenReturn(context);
        when(environment.getArguments()).thenReturn(arguments);
        when(environment.getOperationDefinition()).thenReturn(
                OperationDefinition.newOperationDefinition().operation(operation).build());
        when(environment.getGraphQlContext()).thenReturn(GraphQLContext.newContext().build());
        when(environment.getMergedField()).thenReturn(
                MergedField.newMergedField(Field.newField("field").build()).build());
        return environment;
    }

    private static List<String> splitSegments(String path) {
        return Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Строит цепочку {@link ExecutionStepInfo} от корня к листу: {@code responseKeys} —
     * сегменты пути (алиасы или имена), {@code fieldNames} — реальные имена полей.
     * Для запросов без алиасов оба списка совпадают.
     */
    private static ExecutionStepInfo stepInfoChain(List<String> responseKeys, List<String> fieldNames) {
        ExecutionStepInfo parent = null;
        ResultPath path = ResultPath.rootPath();
        for (int i = 0; i < responseKeys.size(); i++) {
            path = path.segment(responseKeys.get(i));
            ExecutionStepInfo info = mock(ExecutionStepInfo.class);
            when(info.getPath()).thenReturn(path);
            when(info.getField()).thenReturn(
                    MergedField.newMergedField(Field.newField(fieldNames.get(i)).build()).build());
            if (parent != null) {
                when(info.hasParent()).thenReturn(true);
                when(info.getParent()).thenReturn(parent);
            }
            parent = info;
        }
        return parent;
    }
}
