package com.infomaximum.platform.utils;

import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

public class JsonUtils {

    public static <T> T parse(String source, Class<T> type) throws PlatformException {
        try {
            Object tablesJson = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE).parse(source);
            return castTo(type, tablesJson);
        } catch (ParseException e) {
            throw GeneralExceptionBuilder.buildInvalidJsonException(e);
        }
    }

    public static <T> T getValue(String key, Class<T> type, JSONObject source) throws PlatformException {
        Object val = source.get(key);
        if (val == null) {
            throw GeneralExceptionBuilder.buildInvalidJsonException("Value of '" + key + "' is null");
        }
        return castTo(type, val);
    }

    public static <T> T getValueOrDefault(String key, Class<T> type, JSONObject source, T defaultValue) throws PlatformException {
        Object val = source.get(key);
        return val != null ? castTo(type, val) : defaultValue;
    }

    public static int[] getIntArrayValue(String key, JSONObject source) throws PlatformException {
        JSONArray array = getValue(key, JSONArray.class, source);
        int[] value = new int[array.size()];
        for (int i = 0; i < array.size(); ++i) {
            value[i] = castTo(Integer.class, array.get(i));
        }
        return value;
    }

    private static <T> T castTo(Class<T> type, Object value) throws PlatformException {
        if (value.getClass() != type) {
            throw GeneralExceptionBuilder.buildInvalidJsonException(
                    "Unexpected type of value=" + value + ", expected=" + type + ", actual=" + value.getClass());
        }
        return (T) value;
    }
}