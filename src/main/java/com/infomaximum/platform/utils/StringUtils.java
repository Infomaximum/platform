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
        //https://github.com/openjdk/jdk/commit/212a253697b1a5e722bb90ae1140c91175fc028b
        if (str.length() * 3 < 0) {
            throw GeneralExceptionBuilder.buildTooLargeDataException("Required length (%d) exceeds implementation limit".formatted(str.length()));
        }
        return str.getBytes(StandardCharsets.UTF_8);
    }
}
