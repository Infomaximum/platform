package com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute;

import com.infomaximum.platform.Platform;
import com.infomaximum.platform.component.frontend.engine.service.graphqlrequestexecute.struct.GraphQLResponse;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.state.SystemState;
import com.infomaximum.platform.state.SystemStateSnapshot;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тесты {@link GraphQLRequestExecuteServiceNotReady}: проверка формы ответа на любой
 * входящий запрос, корректность переноса {@code state}/{@code progress}/{@code error}
 * в payload и отсутствие утечки чувствительных полей исходного {@code PlatformException}
 * при состоянии {@link SystemState#FAILED}.
 */
public class GraphQLRequestExecuteServiceNotReadyTest {

    /** Для STARTING-фазы стаб отдаёт error=true с кодом system_not_ready и snapshot в parameters. */
    @Test
    public void returnsSystemNotReadyErrorForStarting() throws ExecutionException, InterruptedException {
        Platform platform = mock(Platform.class);
        when(platform.getSystemState()).thenReturn(new SystemStateSnapshot(SystemState.STARTING, 3, 10, null));
        GraphQLRequestExecuteServiceNotReady service = new GraphQLRequestExecuteServiceNotReady(platform);

        @SuppressWarnings("unchecked")
        GraphQLResponse<JSONObject> response = (GraphQLResponse<JSONObject>) service.execute(null).get();

        assertThat(response.error).isTrue();
        assertThat(response.data.get("code")).isEqualTo("system_not_ready");
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) response.data.get("parameters");
        assertThat(parameters.get("state")).isEqualTo("STARTING");
        @SuppressWarnings("unchecked")
        Map<String, Object> progress = (Map<String, Object>) parameters.get("progress");
        assertThat(progress.get("done")).isEqualTo(3);
        assertThat(progress.get("total")).isEqualTo(10);
        assertThat(parameters.containsKey("error")).isFalse();
    }

    /** Для каждой фазы (UPGRADING/STARTING/STOPPING) стаб возвращает соответствующий state. */
    @Test
    public void carriesCurrentStateName() throws ExecutionException, InterruptedException {
        Platform platform = mock(Platform.class);
        for (SystemState state : SystemState.values()) {
            when(platform.getSystemState()).thenReturn(new SystemStateSnapshot(state, 0, 0, null));
            GraphQLRequestExecuteServiceNotReady service = new GraphQLRequestExecuteServiceNotReady(platform);

            @SuppressWarnings("unchecked")
            GraphQLResponse<JSONObject> response = (GraphQLResponse<JSONObject>) service.execute(null).get();

            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) response.data.get("parameters");
            assertThat(parameters.get("state")).isEqualTo(state.name());
        }
    }

    /**
     * Для FAILED-состояния payload включает error.code и error.comment исходного исключения,
     * но НЕ содержит cause, stacktrace и собственные parameters источника (защита от утечки
     * через unauth-эндпоинт).
     */
    @Test
    public void failedStateDoesNotLeakSourceSensitiveFields() throws ExecutionException, InterruptedException {
        PlatformException sourceError = GeneralExceptionBuilder.buildInvalidCredentialsException(
                "user", "secret-username-do-not-leak"
        );
        Platform platform = mock(Platform.class);
        when(platform.getSystemState()).thenReturn(new SystemStateSnapshot(SystemState.FAILED, 1, 4, sourceError));
        GraphQLRequestExecuteServiceNotReady service = new GraphQLRequestExecuteServiceNotReady(platform);

        @SuppressWarnings("unchecked")
        GraphQLResponse<JSONObject> response = (GraphQLResponse<JSONObject>) service.execute(null).get();

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) response.data.get("parameters");
        @SuppressWarnings("unchecked")
        Map<String, Object> errInfo = (Map<String, Object>) parameters.get("error");
        assertThat(errInfo.keySet()).containsExactly("code");
        assertThat(errInfo.get("code")).isEqualTo(sourceError.getCode());
        // Полная JSON-сериализация ответа не должна содержать значений из исходного PlatformException.parameters.
        assertThat(response.data.toString()).doesNotContain("secret-username-do-not-leak");
    }

    /** execute() игнорирует содержимое запроса — для любых аргументов отдаёт одинаковую форму ответа. */
    @Test
    public void executeIgnoresRequestContent() throws ExecutionException, InterruptedException {
        Platform platform = mock(Platform.class);
        when(platform.getSystemState()).thenReturn(new SystemStateSnapshot(SystemState.STARTING, 0, 0, null));
        GraphQLRequestExecuteServiceNotReady service = new GraphQLRequestExecuteServiceNotReady(platform);

        @SuppressWarnings("unchecked")
        GraphQLResponse<JSONObject> response = (GraphQLResponse<JSONObject>) service.execute(null).get();

        assertThat(response.error).isTrue();
        assertThat(response.data.get("code")).isEqualTo("system_not_ready");
    }
}
