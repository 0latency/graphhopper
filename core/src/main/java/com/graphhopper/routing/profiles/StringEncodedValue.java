package com.graphhopper.routing.profiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class holds a string array and stores only a number (typically the array index via IntEncodedValue)
 * to restore this information.
 */
public final class StringEncodedValue extends IntEncodedValue {
    private final String[] map;

    public StringEncodedValue(String name, List<String> values, String defaultValue) {
        super(name, (int) Long.highestOneBit(values.size()));

        // we want to use binarySearch so we need to sort the list
        // TODO should we simply use a separate Map<String, Int>?
        Collections.sort(values);
        map = values.toArray(new String[]{});
        this.defaultValue = Arrays.binarySearch(map, defaultValue);
        if (this.defaultValue < 0)
            throw new IllegalArgumentException("default value " + defaultValue + " not found");
    }

    private int getIndex(String value) {
        if (value == null)
            return defaultValue;
        int res = Arrays.binarySearch(map, value);
        if (res < 0)
            return defaultValue;
        return res;
    }

    public final int toStorageFormat(int flags, String value) {
        int intValue = getIndex(value);
        return super.toStorageFormat(flags, intValue);
    }

    public final String fromStorageFormatToString(int flags) {
        int value = super.fromStorageFormatToInt(flags);
        if (value < 0 || value >= map.length)
            return map[defaultValue];
        return map[value];
    }
}
