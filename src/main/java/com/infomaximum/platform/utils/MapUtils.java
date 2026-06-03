package com.infomaximum.platform.utils;

import java.util.Arrays;
import java.util.HashMap;

public class MapUtils {

    public static <K, V> boolean equals(HashMap<K, V[]> map, HashMap<K, V[]> otherMap) {
        //Когда HashMap хранит массивы в качестве значений, метод Map.equals() завершается ошибкой,
        // поскольку он проверяет равенство ссылок, а не равенство содержимого.
        if (map == otherMap) {
            return true;
        }
        if (map == null || otherMap == null || map.size() != otherMap.size()) {
            return false;
        }
        for (var entry : map.entrySet()) {
            V[] value = entry.getValue();
            V[] otherValue = otherMap.get(entry.getKey());
            if (!Arrays.equals(value, otherValue)) {
                return false;
            }
        }
        return true;
    }
}
