package com.infomaximum.platform.component.frontend.request;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет слот «намерения ответа» {@link GRequestHttp}: накопление значений {@code Set-Cookie}
 * и дополнительных заголовков, дедупликацию cookie по имени (last-writer-wins), пустое состояние
 * по умолчанию.
 */
class GRequestHttpResponseSlotTest {

    /** До добавления слот пуст — геттеры возвращают {@code null}. */
    @Test
    void slotIsNullByDefault() {
        GRequestHttp request = new GRequestHttp.Builder().build();

        assertThat(request.getResponseSetCookies()).isNull();
        assertThat(request.getResponseHeaders()).isNull();
    }

    /** Несколько {@code Set-Cookie} накапливаются в порядке добавления. */
    @Test
    void setCookiesAccumulateInOrder() {
        GRequestHttp request = new GRequestHttp.Builder().build();

        request.addResponseSetCookie("a=1");
        request.addResponseSetCookie("b=2");

        List<String> cookies = request.getResponseSetCookies();
        assertThat(cookies).containsExactly("a=1", "b=2");
    }

    /**
     * Повторный {@code Set-Cookie} с тем же именем cookie заменяет прежний (last-writer-wins),
     * а не добавляется вторым: сценарий «продление сессии в auth-фазе, затем удаляющая cookie
     * на logout» — в ответе остаётся только удаляющая.
     */
    @Test
    void sameNameCookieReplacedLastWins() {
        GRequestHttp request = new GRequestHttp.Builder().build();

        request.addResponseSetCookie("session=newtoken; Path=/; Max-Age=60; HttpOnly; SameSite=Lax");
        request.addResponseSetCookie("session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");

        List<String> cookies = request.getResponseSetCookies();
        assertThat(cookies).containsExactly("session=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    }

    /** Дополнительный заголовок переносится с тем же именем и значением. */
    @Test
    void responseHeaderIsStored() {
        GRequestHttp request = new GRequestHttp.Builder().build();

        request.addResponseHeader("X-CSRF-Token", "csrf-value");

        Map<String, String> headers = request.getResponseHeaders();
        assertThat(headers).containsEntry("X-CSRF-Token", "csrf-value");
    }
}
