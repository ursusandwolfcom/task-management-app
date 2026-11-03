package com.example.taskmanagement.integration.email.application;

/** Minimal branded HTML wrapper; plain text remains the source of truth. */
public final class EmailHtmlFormatter {
    private EmailHtmlFormatter() {
    }

    public static String wrap(String brand, String title, String textBody) {
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
                + "</title></head><body style=\"margin:0;padding:0;background:#e8eef2;"
                + "font-family:'Segoe UI',Tahoma,sans-serif;color:#0f172a;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""
                + " style=\"background:#e8eef2;padding:24px 12px;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\""
                + " style=\"background:#ffffff;border:1px solid #cbd5e1;padding:28px 32px;\">"
                + "<tr><td style=\"font-size:20px;letter-spacing:0.06em;text-transform:uppercase;"
                + "color:#0f766e;\">" + safeBrand + "</td></tr>"
                + "<tr><td style=\"padding-top:16px;font-size:18px;font-weight:600;\">"
                + safeTitle + "</td></tr>"
                + "<tr><td style=\"padding-top:12px;font-size:15px;line-height:1.55;color:#334155;\">"
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
