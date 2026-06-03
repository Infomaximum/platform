package com.infomaximum.platform.sdk.subscription;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.exception.PlatformExceptionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AsyncOperationResultStoreTest {

    private AsyncOperationResultStore store;

    @BeforeEach
    void setUp() {
        store = new AsyncOperationResultStore();
    }

    // 1. register создаёт Running
    @Test
    void registerCreatesRunningState() {
        UUID id = store.register();

        assertNotNull(id);
        assertInstanceOf(AsyncOperationResult.Running.class, store.get(id));
    }

    // 2. register возвращает уникальные UUID
    @Test
    void registerReturnsUniqueIds() {
        UUID id1 = store.register();
        UUID id2 = store.register();

        assertNotEquals(id1, id2);
    }

    // 3. complete переводит в Completed с данными
    @Test
    void completeTransitionsToCompleted() {
        UUID id = store.register();

        store.complete(id, "done");

        if (store.get(id) instanceof AsyncOperationResult.Completed(var data)) {
            assertEquals("done", data);
        } else {
            fail("Expected Completed");
        }
    }

    // 4. complete с null результатом
    @Test
    void completeWithNullResult() {
        UUID id = store.register();

        store.complete(id, null);

        if (store.get(id) instanceof AsyncOperationResult.Completed(var data)) {
            assertNull(data);
        } else {
            fail("Expected Completed");
        }
    }

    // 5. fail переводит в Failed с PlatformException
    @Test
    void failTransitionsToFailed() {
        UUID id = store.register();

        store.fail(id, buildException("test_error"));

        if (store.get(id) instanceof AsyncOperationResult.Failed(var ex)) {
            assertEquals("test_error", ex.getCode());
        } else {
            fail("Expected Failed");
        }
    }

    // 6. get по неизвестному id возвращает null
    @Test
    void getReturnsNullForUnknownId() {
        assertNull(store.get(UUID.randomUUID()));
    }

    // 7. Completed хранит Serializable корректно
    @Test
    void completedPreservesSerializableResult() {
        UUID id = store.register();

        Map<String, Integer> complexResult = Map.of("count", 42, "total", 100);
        store.complete(id, (java.io.Serializable) complexResult);

        if (store.get(id) instanceof AsyncOperationResult.Completed(var data)) {
            assertEquals(Map.of("count", 42, "total", 100), data);
        } else {
            fail("Expected Completed");
        }
    }

    // 8. Failed хранит code и parameters из PlatformException
    @Test
    void failedPreservesExceptionDetails() {
        UUID id = store.register();

        store.fail(id, buildException("code", Map.of("param1", "value1")));

        if (store.get(id) instanceof AsyncOperationResult.Failed(var ex)) {
            assertEquals("code", ex.getCode());
            assertEquals("value1", ex.getParameters().get("param1"));
        } else {
            fail("Expected Failed");
        }
    }

    // 9. Запись протухает после TTL
    @Test
    void entryExpiresAfterTtl() throws InterruptedException {
        UUID id = UUID.randomUUID();
        store.put(id, new AsyncOperationResult.Running(), 500);

        assertNotNull(store.get(id));

        Thread.sleep(600);

        assertNull(store.get(id));
    }

    // 10. Запись доступна до истечения TTL
    @Test
    void entryAvailableBeforeTtl() throws InterruptedException {
        UUID id = UUID.randomUUID();
        store.put(id, new AsyncOperationResult.Running(), 2000);

        Thread.sleep(500);

        assertNotNull(store.get(id));
    }

    // 11-12. Две операции независимы
    @Test
    void multipleOperationsIndependent() {
        UUID id1 = store.register();
        UUID id2 = store.register();

        store.complete(id1, "result1");
        store.fail(id2, buildException("error2"));

        if (store.get(id1) instanceof AsyncOperationResult.Completed(var data)) {
            assertEquals("result1", data);
        } else {
            fail("Expected Completed for id1");
        }

        if (store.get(id2) instanceof AsyncOperationResult.Failed(var ex)) {
            assertEquals("error2", ex.getCode());
        } else {
            fail("Expected Failed for id2");
        }
    }

    private static PlatformException buildException(String code) {
        return new PlatformExceptionFactory(null).build(code, null, null, null);
    }

    private static PlatformException buildException(String code, Map<String, Object> parameters) {
        return new PlatformExceptionFactory(null).build(code, null, parameters, null);
    }
}
