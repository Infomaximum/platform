package com.infomaximum.platform.component.frontend.request;

import com.infomaximum.cluster.graphql.struct.GRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты {@link GRequestHttp}: связь Builder ↔ конструктор ↔ геттер для
 * заголовка {@code X-Request-Id}.
 */
class GRequestHttpTest {

    /**
     * Builder с {@code withXRequestId} прокидывает значение в построенный
     * {@link GRequestHttp}.
     */
    @Test
    void builderPropagatesXRequestIdToGetter() {
        String requestId = UUID.randomUUID().toString();

        GRequestHttp gRequest = baseBuilder()
                .withXRequestId(requestId)
                .build();

        assertThat(gRequest.getXRequestId()).isEqualTo(requestId);
    }

    /**
     * Без вызова {@code withXRequestId} геттер возвращает {@code null} —
     * заголовка в запросе не было.
     */
    @Test
    void builderWithoutXRequestIdReturnsNull() {
        GRequestHttp gRequest = baseBuilder().build();

        assertThat(gRequest.getXRequestId()).isNull();
    }

    /**
     * Legacy 10-параметровый конструктор не задаёт {@code xRequestId} —
     * геттер возвращает {@code null} (backward-compat).
     */
    @Test
    void legacyConstructorLeavesXRequestIdNull() {
        @SuppressWarnings("deprecation")
        GRequestHttp gRequest = new GRequestHttp(
                Instant.now(),
                new GRequest.RemoteAddress("127.0.0.1"),
                "{ __typename }",
                new HashMap<>(),
                null,
                null,
                new HashMap<>(),
                new HashMap<>(),
                null,
                null
        );

        assertThat(gRequest.getXRequestId()).isNull();
    }

    private GRequestHttp.Builder baseBuilder() {
        return new GRequestHttp.Builder()
                .withInstantRequest(Instant.now())
                .withRemoteAddress(new GRequest.RemoteAddress("127.0.0.1"))
                .withQuery("{ __typename }")
                .withQueryVariables(new HashMap<>())
                .withParameters(new HashMap<>())
                .withAttributes(new HashMap<>());
    }
}
