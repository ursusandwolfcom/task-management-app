package com.example.taskmanagement.integration.email.application;

/** Minimal branded HTML wrapper; plain text remains the source of truth. */
public final class EmailHtmlFormatter {
    public enum Theme {
        DEFAULT("#e8eef2", "#ffffff", "#cbd5e1", "#0f766e", "#0f172a", "#334155"),
        SLATE("#0f172a", "#1e293b", "#334155", "#38bdf8", "#f8fafc", "#cbd5e1");

        private final String pageBackground;
        private final String cardBackground;
        private final String border;
        private final String brand;
        private final String title;
        private final String body;

        Theme(String pageBackground, String cardBackground, String border, String brand, String title, String body) {
            this.pageBackground = pageBackground;
            this.cardBackground = cardBackground;
            this.border = border;
            this.brand = brand;
            this.title = title;
            this.body = body;
        }

        public static Theme from(String value) {
            if (value == null || value.isBlank()) {
                return DEFAULT;
            }
            return switch (value.strip().toLowerCase()) {
                case "slate" -> SLATE;
                case "default" -> DEFAULT;
                default -> throw new IllegalArgumentException("Unknown mail theme: " + value);
            };
        }
    }

    private EmailHtmlFormatter() {
    }

    public static String wrap(String brand, String title, String textBody) {
        return wrap(brand, title, textBody, Theme.DEFAULT);
    }

    public static String wrap(String brand, String title, String textBody, Theme theme) {
        Theme colors = theme == null ? Theme.DEFAULT : theme;
        String safeBrand = escape(blank(brand, "ProjectFlow"));
        String safeTitle = escape(blank(title, "Уведомление"));
        StringBuilder paragraphs = new StringBuilder();
        for (String line : blank(textBody, "").split("\n", -1)) {
            if (line.isBlank()) {
                paragraphs.append("<br/>");
            } else if (isLink(line)) {
                String href = escapeAttr(line.strip());
                paragraphs.append("<p><a href=\"").append(href).append("\">")
                        .append(escape(line.strip())).append("</a></p>");
            } else {
                paragraphs.append("<p>").append(escape(line)).append("</p>");
            }
        }
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><title>"
                + safeTitle
                + "</title></head><body style=\"margin:0;padding:0;background:"
                + colors.pageBackground
                + ";font-family:'Segoe UI',Tahoma,sans-serif;color:"
                + colors.title
                + ";\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
                + " style=\"background:" + colors.pageBackground + ";padding:24px 12px;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\""
                + " style=\"background:" + colors.cardBackground + ";border:1px solid " + colors.border
                + ";padding:28px 32px;\">"
                + "<tr><td style=\"font-size:20px;letter-spacing:0.06em;text-transform:uppercase;"
                + "color:" + colors.brand + ";\">" + safeBrand + "</td></tr>"
                + "<tr><td style=\"padding-top:16px;font-size:18px;font-weight:600;color:"
                + colors.title + ";\">"
                + safeTitle + "</td></tr>"
                + "<tr><td style=\"padding-top:12px;font-size:15px;line-height:1.55;color:"
                + colors.body + ";\">"
                + paragraphs + "</td></tr></table></td></tr></table></body></html>";
    }

    private static boolean isLink(String line) {
        String value = line.strip();
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("#/");
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeAttr(String value) {
        return escape(value).replace("'", "&#39;");
    }
}
