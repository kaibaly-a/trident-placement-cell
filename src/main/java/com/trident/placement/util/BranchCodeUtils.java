package com.trident.placement.util;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class BranchCodeUtils {

    /**
     * Normalizes a list of branch codes.
     * Trims spaces, converts to uppercase, and removes nulls/blanks.
     */
    public static List<String> normalizeList(List<String> branches) {
        if (branches == null || branches.isEmpty()) {
            return Collections.emptyList();
        }
        return branches.stream()
                .filter(b -> b != null && !b.isBlank())
                .map(b -> b.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Converts a list of branch codes to a normalized Set.
     */
    public static Set<String> toSet(List<String> branches) {
        if (branches == null || branches.isEmpty()) {
            return Collections.emptySet();
        }
        return branches.stream()
                .filter(b -> b != null && !b.isBlank())
                .map(b -> b.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    /**
     * Compares two lists of branches to see if they contain the same unique elements.
     */
    public static boolean sameBranchSet(List<String> list1, List<String> list2) {
        Set<String> set1 = toSet(list1);
        Set<String> set2 = toSet(list2);
        return set1.equals(set2);
    }
}
