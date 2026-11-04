package com.example.taskmanagement.attachment.application;

/** Resolves the Dropbox folder root for app-folder vs team-folder storage modes. */
public final class DropboxRootFolder {
    private DropboxRootFolder() {
    }

    public static String resolve(String mode, String appFolder, String teamFolder) {
        String normalizedMode = mode == null || mode.isBlank() ? "app" : mode.strip().toLowerCase();
        return switch (normalizedMode) {
            case "app" -> normalize(blank(appFolder, "/projectflow"));
            case "team" -> {
                String folder = blank(teamFolder, "");
                if (folder.isEmpty()) {
                    throw new IllegalArgumentException("dropbox.team-folder is required when dropbox.mode=team");
                }
                yield normalize(folder);
            }
            default -> throw new IllegalArgumentException("dropbox.mode must be app or team");
        };
    }

    public static String userFolder(String root, long userId) {
        return normalize(root) + "/u" + userId;
    }

    static String normalize(String folder) {
        String trimmed = folder == null ? "" : folder.strip();
        if (trimmed.isEmpty()) {
            trimmed = "/projectflow";
        }
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
