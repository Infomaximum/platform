package com.infomaximum.platform.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты колбэка вытеснения {@link TimedCache}: он вызывается при истечении TTL и
 * НЕ вызывается на явный {@code remove} — на этом контракте держится одноразовое
 * «забирание» записи без преждевременной чистки ресурса (см. DownloadStore).
 */
class TimedCacheTest {

    /** При истечении TTL запись вытесняется, и колбэк получает её значение. */
    @Test
    void onExpireCalledWhenEntryExpires() throws InterruptedException {
        List<String> expired = new ArrayList<>();
        TimedCache<String, String> cache = new TimedCache<>(TimeUnit.MINUTES.toMillis(1), expired::add);

        cache.put("k", "v", 1, TimeUnit.MILLISECONDS);
        Thread.sleep(20);

        assertThat(cache.get("k")).isNull();
        assertThat(expired).containsExactly("v");
    }

    /** {@code poll} извлекает значение атомарно и одноразово: повторный вызов возвращает null. */
    @Test
    void pollIsSingleUse() {
        TimedCache<String, String> cache = new TimedCache<>(TimeUnit.MINUTES.toMillis(1));
        cache.put("k", "v");

        assertThat(cache.poll("k")).isEqualTo("v");
        assertThat(cache.poll("k")).isNull();
    }

    /** {@code poll} просроченной записи возвращает null и срабатывает колбэк вытеснения. */
    @Test
    void pollExpiredReturnsNullAndFiresOnExpire() throws InterruptedException {
        List<String> expired = new ArrayList<>();
        TimedCache<String, String> cache = new TimedCache<>(TimeUnit.MINUTES.toMillis(1), expired::add);

        cache.put("k", "v", 1, TimeUnit.MILLISECONDS);
        Thread.sleep(20);

        assertThat(cache.poll("k")).isNull();
        assertThat(expired).containsExactly("v");
    }

    /** Явный {@code remove} забирает запись без срабатывания колбэка вытеснения. */
    @Test
    void onExpireNotCalledOnExplicitRemove() {
        List<String> expired = new ArrayList<>();
        TimedCache<String, String> cache = new TimedCache<>(TimeUnit.MINUTES.toMillis(1), expired::add);

        cache.put("k", "v");
        cache.remove("k");

        assertThat(cache.get("k")).isNull();
        assertThat(expired).isEmpty();
    }
}
