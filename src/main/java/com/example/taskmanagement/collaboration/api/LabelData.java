package com.example.taskmanagement.collaboration.api;

import com.example.taskmanagement.shared.validation.NoNul;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record LabelData(@NotBlank @Size(max = 100) @NoNul String name,
        @NotBlank @Pattern(regexp = "#[0-9A-F]{6}") String color) {
    public LabelData {
        name = name == null ? null : name.strip();
        color = color == null ? null : color.toUpperCase(Locale.ROOT);
    }

    public String nameKey() {
        return name.toLowerCase(Locale.ROOT);
    }
}
