package com.infomaximum.platform.utils;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class StringUtils {

    /**
     * <p>Checks if a CharSequence is empty ("") or null.</p>
     *
     * <pre>
     * StringUtils.isEmpty(null)      = true
     * StringUtils.isEmpty("")        = true
     * StringUtils.isEmpty(" ")       = false
     * StringUtils.isEmpty("bob")     = false
     * StringUtils.isEmpty("  bob  ") = false
     * </pre>
     *
     * <p>NOTE: This method changed in Lang version 2.0.
     * It no longer trims the CharSequence.
     * That functionality is available in isBlank().</p>
     *
     * @param cs  the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is empty or null
     * @since 3.0 Changed signature from isEmpty(String) to isEmpty(CharSequence)
     */
    public static boolean isEmpty(final CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static byte[] getBytesUTF8(String str) throws PlatformException {
        //В кодировке UTF-8 длина строки может превышать её в 3 раза.
        //https://github.com/openjdk/jdk/commit/212a253697b1a5e722bb90ae1140c91175fc028b
        if (str.length() * 3 < 0) {
            throw GeneralExceptionBuilder.buildTooLargeDataException("Required length (%d) exceeds implementation limit".formatted(str.length()));
        }
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Сериализует JSON-объект в строку с жёстким лимитом длины. Пишет объект тем же
     * стилем, что и {@link JSONObject#toString()} ({@link JSONValue#COMPRESSION}), но
     * прерывает сериализацию, как только длина результата превысит {@code maxChars} —
     * не материализуя гигантскую строку и не доходя до предела длины массива.
     *
     * @param json     сериализуемый объект.
     * @param maxChars максимальная длина результата в символах; при превышении — отбой.
     * @return сериализованный JSON длиной не более {@code maxChars} символов.
     * @throws PlatformException {@code too_large_data} — результат превысил {@code maxChars}.
     */
    public static @NonNull String toLimitedJsonString(@NonNull JSONObject json, int maxChars) throws PlatformException {
        LimitedAppendable out = new LimitedAppendable(maxChars);
        try {
            json.writeJSONString(out, JSONValue.COMPRESSION);
        } catch (LimitExceededException e) {
            throw GeneralExceptionBuilder.buildTooLargeDataException(
                    "Response length exceeds limit (%d chars)".formatted(maxChars));
        } catch (IOException e) {
            // LimitedAppendable пишет в память и не бросает IOException — недостижимо.
            throw new IllegalStateException(e);
        }
        return out.result();
    }

    /** Сигнал превышения лимита длины при сериализации; ловится в {@link #toLimitedJsonString}. */
    private static final class LimitExceededException extends RuntimeException {
        private LimitExceededException() {
            super(null, null, false, false);
        }
    }

    /**
     * Приёмник символов поверх {@link StringBuilder} с жёстким лимитом длины: как только
     * добавление вышло бы за {@code maxChars}, бросает {@link LimitExceededException}.
     */
    private static final class LimitedAppendable implements Appendable {

        private final StringBuilder sb = new StringBuilder();
        private final int maxChars;

        private LimitedAppendable(int maxChars) {
            this.maxChars = maxChars;
        }

        private void ensureCapacity(int added) {
            // long: sb.length()+added может превысить Integer.MAX_VALUE (один
            // гигантский чанк) и переполнить int, ложно пройдя проверку.
            if ((long) sb.length() + added > maxChars) {
                throw new LimitExceededException();
            }
        }

        @Override
        public Appendable append(CharSequence csq) {
            CharSequence s = (csq == null) ? "null" : csq;
            ensureCapacity(s.length());
            sb.append(s);
            return this;
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) {
            CharSequence s = (csq == null) ? "null" : csq;
            ensureCapacity(end - start);
            sb.append(s, start, end);
            return this;
        }

        @Override
        public Appendable append(char c) {
            ensureCapacity(1);
            sb.append(c);
            return this;
        }

        private String result() {
            return sb.toString();
        }
    }
}
