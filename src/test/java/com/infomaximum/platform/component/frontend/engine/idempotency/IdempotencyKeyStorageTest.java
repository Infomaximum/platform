package com.infomaximum.platform.component.frontend.engine.idempotency;

import com.infomaximum.cluster.graphql.struct.GRequest;
import com.infomaximum.platform.component.frontend.engine.idempotency.IdempotencyResponse.State;
import com.infomaximum.platform.component.frontend.request.GRequestHttp;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

public class IdempotencyKeyStorageTest {

    private IdempotencyKeyStorage idempotencyKeyStorage;

    @BeforeEach
    public void init() {
        this.idempotencyKeyStorage = new IdempotencyKeyStorage(null);
    }

    @Test
    public void idempotencyKeyStorageTest1() throws InterruptedException {
        var idempotencyKey = UUID.randomUUID().toString();
        var idempotencyKey2 = UUID.randomUUID().toString();
        var requestHttpBuilder = new GRequestHttp.Builder()
                .withInstantRequest(Instant.now())
                .withRemoteAddress(new GRequest.RemoteAddress("0.0.0.1"))
                .withQuery("{query}")
                .withQueryVariables(new HashMap<>())
                .withOperationName("operation")
                .withXTraceId(UUID.randomUUID().toString())
                .withXRetryCount(0)
                .withIdempotencyKey(idempotencyKey);
        idempotencyKeyStorage.put(idempotencyKey2, new IdempotencyResponse(null, State.READY, null, null, null), 1000);
        idempotencyKeyStorage.put(idempotencyKey, new IdempotencyResponse(requestHttpBuilder.build(), State.EXECUTE, null, null, null));
        Assertions.assertEquals(2, idempotencyKeyStorage.size());
        Assertions.assertNotNull(idempotencyKeyStorage.get(idempotencyKey));
        Thread.sleep(2000);

        IdempotencyResponse idempotencyResponse = idempotencyKeyStorage.get(idempotencyKey2);
        Assertions.assertNull(idempotencyResponse);
        Assertions.assertEquals(1, idempotencyKeyStorage.size());

        idempotencyResponse = idempotencyKeyStorage.get(idempotencyKey);
        Assertions.assertNotNull(idempotencyResponse);
        Assertions.assertEquals(State.EXECUTE, idempotencyResponse.state());
        Assertions.assertTrue(idempotencyResponse.isEqualGRequestHttp(requestHttpBuilder.build()));

        for (int i = 0; i < 199; i++) {
            idempotencyKeyStorage.put(UUID.randomUUID().toString(), new IdempotencyResponse(null, State.READY, null, null, null), 1000);
        }
        Assertions.assertEquals(200, idempotencyKeyStorage.size());
        Thread.sleep(2000);
        Assertions.assertEquals(200, idempotencyKeyStorage.size());
        idempotencyKeyStorage.put(UUID.randomUUID().toString(), new IdempotencyResponse(null, State.READY, null, null, null));
        Assertions.assertEquals(2, idempotencyKeyStorage.size());

    }

    @Test
    public void idempotencyResponseTest1() throws InterruptedException {
        var idempotencyKey = UUID.randomUUID().toString();
        var requestHttpBuilder = new GRequestHttp.Builder()
                .withInstantRequest(Instant.now())
                .withRemoteAddress(new GRequest.RemoteAddress("0.0.0.1"))
                .withQuery("{query}")
                .withQueryVariables(new HashMap<>(){{
                    put("1", "1");
                }})
                .withOperationName("operation")
                .withXTraceId(idempotencyKey)
                .withXRetryCount(0)
                .withIdempotencyKey(idempotencyKey)
                .withParameters(new HashMap<>(){{
                    put("query", new String[]{"{query\n{server}\n}"});
                    put("api_key", new String[]{"apiKeyValue"});
                }})
                .withAttributes(null)
                .withCookies(null);
        var idempotencyResponse = new IdempotencyResponse(requestHttpBuilder.build(), State.EXECUTE, null, null, null);
        Assertions.assertTrue(idempotencyResponse.isEqualGRequestHttp(requestHttpBuilder.build()));
        Assertions.assertTrue(idempotencyResponse.isEqualGRequestHttp(
                requestHttpBuilder
                        .withParameters(new HashMap<>() {{
                            put("query", new String[]{"{query\n{server}\n}"});
                            put("api_key", new String[]{"apiKeyValue"});
                        }})
                        .build()));
        Assertions.assertFalse(idempotencyResponse.isEqualGRequestHttp(
                requestHttpBuilder
                        .withCookies(new Cookie[]{new Cookie("session", "12345")})
                        .build()));
        Assertions.assertFalse(idempotencyResponse.isEqualGRequestHttp(
                requestHttpBuilder
                        .withAttributes(new HashMap<>() {{
                            put("1", new String[]{"1"});
                        }})
                        .build()));
        Assertions.assertFalse(idempotencyResponse.isEqualGRequestHttp(
                requestHttpBuilder
                        .withParameters(new HashMap<>())
                        .build()));
        Assertions.assertFalse(idempotencyResponse.isEqualGRequestHttp(
                requestHttpBuilder
                        .withQueryVariables(new HashMap<>())
                        .build()));
        Assertions.assertFalse(idempotencyResponse.isEqualGRequestHttp(
                requestHttpBuilder
                        .withRemoteAddress(new GRequest.RemoteAddress("127.0.0.1"))
                        .build()));
        Assertions.assertFalse(idempotencyResponse.isEqualGRequestHttp(
                requestHttpBuilder
                        .withIdempotencyKey("o")
                        .build()));
        Assertions.assertFalse(idempotencyResponse.isEqualGRequestHttp(
                requestHttpBuilder
                        .withOperationName("o")
                        .build()));
        Assertions.assertFalse(idempotencyResponse.isEqualGRequestHttp(
                requestHttpBuilder
                        .withQuery("o")
                        .build()));
    }
}
