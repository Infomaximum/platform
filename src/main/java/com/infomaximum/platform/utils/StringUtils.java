package com.infomaximum.platform.utils;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;

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
        //Для очень больших значений (например, при переполнении 32-битного целого числа) предварительно вычисляется точный размер.
        if (str.length() * 3 < 0) {
            long allocLen = StringUtils.computeSizeUTF8_UTF16(str.toCharArray(), true);
            if (allocLen > (long) Integer.MAX_VALUE) {
                throw GeneralExceptionBuilder.buildTooLargeDataException("Required length (%d) exceeds implementation limit".formatted(str.length()));
            }
        }
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@return the exact size required to UTF_8 encode this UTF16 string}
     * @param val character array
     * @param doReplace true to replace unmappable characters
     */
    //https://github.com/openjdk/jdk/commit/212a253697b1a5e722bb90ae1140c91175fc028b
    private static long computeSizeUTF8_UTF16(char[] val, boolean doReplace) {
        long dp = 0L;
        int sp = 0;
        int sl = val.length;

        while (sp < sl) {
            char c = val[sp++];
            if (c < 0x80) {
                dp++;
            } else if (c < 0x800) {
                dp += 2;
            } else if (Character.isSurrogate(c)) {
                int uc = -1;
                char c2;
                if (Character.isHighSurrogate(c) && sp < sl &&
                        Character.isLowSurrogate(c2 = val[sp])) {
                    uc = Character.toCodePoint(c, c2);
                }
                if (uc < 0) {
                    if (doReplace) {
                        dp++;
                    } else {
                        throwUnmappable(sp - 1);
                    }
                } else {
                    dp += 4;
                    sp++;  // 2 chars
                }
            } else {
                // 3 bytes, 16 bits
                dp += 3;
            }
        }
        return dp;
    }

    private static void throwUnmappable(int off) {
        String msg = "malformed input off : " + off + ", length : 1";
        throw new IllegalArgumentException(msg, new UnmappableCharacterException(1));
    }
}
