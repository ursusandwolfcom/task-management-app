package com.example.taskmanagement.repository;

import java.util.Locale;

public final class SearchPatterns {
    private SearchPatterns() {
    }

    public static String containsLiteral(String name) {
        String escaped = name.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
