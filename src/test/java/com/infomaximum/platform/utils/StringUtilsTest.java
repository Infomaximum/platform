package com.infomaximum.platform.utils;

import com.infomaximum.platform.exception.PlatformException;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StringUtilsTest {

    private static JSONObject sample() {
        JSONObject data = new JSONObject();
        data.put("name", "value");
        data.put("count", 42);
        data.put("nested", new JSONObject().appendField("k", "тест-значение"));
        JSONObject root = new JSONObject();
        root.put("data", data);
        return root;
    }

    /** Под лимитом результат байт-в-байт совпадает с {@link JSONObject#toString()}. */
    @Test
    @DisplayName("toLimitedJsonString под лимитом идентичен toString")
    public void producesSameOutputAsToStringUnderLimit() throws PlatformException {
        JSONObject json = sample();
        String expected = json.toString();

        String actual = StringUtils.toLimitedJsonString(json, expected.length() + 1_000);

        assertThat(actual).isEqualTo(expected);
    }

    /** Длина ровно на лимите допустима — отбоя нет. */
    @Test
    @DisplayName("toLimitedJsonString на границе лимита не отбивает")
    public void acceptsOutputExactlyAtLimit() throws PlatformException {
        JSONObject json = sample();
        int exactLength = json.toString().length();

        String actual = StringUtils.toLimitedJsonString(json, exactLength);

        assertThat(actual).isEqualTo(json.toString());
    }

    /** Превышение лимита на один символ — отбой {@code too_large_data}, не OOM. */
    @Test
    @DisplayName("toLimitedJsonString при превышении бросает too_large_data")
    public void throwsTooLargeDataWhenOverLimit() {
        JSONObject json = sample();
        int overByOne = json.toString().length() - 1;

        assertThatThrownBy(() -> StringUtils.toLimitedJsonString(json, overByOne))
                .isInstanceOf(PlatformException.class)
                .extracting(e -> ((PlatformException) e).getCode())
                .isEqualTo("too_large_data");
    }

    /** Отбой не зависит от абсолютной длины — маленький лимит отбивает любой непустой ответ. */
    @Test
    @DisplayName("toLimitedJsonString с маленьким лимитом отбивает большой ответ")
    public void throwsTooLargeDataForLargeValueUnderSmallLimit() {
        JSONObject json = new JSONObject();
        json.put("blob", "x".repeat(10_000));

        assertThatThrownBy(() -> StringUtils.toLimitedJsonString(json, 100))
                .isInstanceOf(PlatformException.class)
                .extracting(e -> ((PlatformException) e).getCode())
                .isEqualTo("too_large_data");
    }

    /** UTF-8-конвертация без потерь для не-ASCII. */
    @Test
    @DisplayName("getBytesUTF8 кодирует не-ASCII без потерь")
    public void getBytesUTF8EncodesNonAscii() throws PlatformException {
        byte[] bytes = StringUtils.getBytesUTF8("тест");

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("тест");
    }
}
