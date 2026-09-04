package com.finance.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.tracker.config.AppProperties;
import com.finance.tracker.exception.AppException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImportService {

    private static final List<String> CATEGORY_KEYS = List.of(
            "salary", "freelance", "dividend", "interest", "rent", "groceries", "dining",
            "transport", "utilities", "shopping", "health", "entertainment", "emi", "investment", "other");

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ImportService(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> parseFile(byte[] bytes, String filename, String mime) {
        String text;
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if ((mime != null && mime.contains("pdf")) || lower.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                text = new PDFTextStripper().getText(doc);
            } catch (Exception e) {
                throw AppException.badRequest("Could not parse PDF");
            }
        } else {
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        List<Raw> rows = parseStatementText(text);
        if (rows.isEmpty()) {
            throw AppException.badRequest("Could not parse any transactions from the uploaded file");
        }
        return classify(rows);
    }

    record Raw(String title, int amount, String date, String note) {}

    List<Raw> parseStatementText(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return List.of();
        String first = trimmed.split("\\R", 2)[0];
        boolean looksCsv = trimmed.contains(",") && first.toLowerCase(Locale.ROOT).matches(".*(date|amount|description|narration|title).*");
        if (looksCsv) {
            return parseCsv(trimmed);
        }
        List<Raw> out = new ArrayList<>();
        Pattern datePat = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})");
        Pattern amtPat = Pattern.compile("[-+]?\\s*₹?\\s*[\\d,]+\\.?\\d*");
        for (String line : trimmed.split("\\R")) {
            line = line.trim();
            Matcher dm = datePat.matcher(line);
            Matcher am = amtPat.matcher(line);
            if (!dm.find() || !am.find()) continue;
            String date = parseDate(dm.group(1));
            Integer amount = parseAmount(am.group());
            if (date == null || amount == null) continue;
            String title = line.replace(dm.group(), "").replace(am.group(), "").replace("|", " ").trim();
            if (title.isEmpty()) title = "Imported";
            out.add(new Raw(title, amount, date, null));
        }
        return out;
    }

    private List<Raw> parseCsv(String text) {
        String[] lines = text.split("\\R");
        if (lines.length < 2) return List.of();
        String[] headers = splitCsv(lines[0]);
        List<Raw> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            String[] cols = splitCsv(lines[i]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.length && c < cols.length; c++) {
                row.put(headers[c].trim(), cols[c].trim());
            }
            String title = pick(row, "title", "description", "narration", "particulars", "details");
            Integer debit = parseAmount(pick(row, "debit", "withdrawal", "dr"));
            Integer credit = parseAmount(pick(row, "credit", "deposit", "cr"));
            Integer amount = parseAmount(pick(row, "amount"));
            if (amount == null) {
                if (debit != null && debit != 0) amount = -Math.abs(debit);
                else if (credit != null) amount = Math.abs(credit);
            }
            String date = parseDate(pick(row, "date", "txn_date", "transaction_date", "value_date"));
            if (title.isBlank() || amount == null || date == null) continue;
            String note = pick(row, "note", "remarks");
            out.add(new Raw(title, amount, date, note.isBlank() ? null : note));
        }
        return out;
    }

    private String[] splitCsv(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(String[]::new);
    }

    private String pick(Map<String, String> row, String... names) {
        for (String name : names) {
            for (Map.Entry<String, String> e : row.entrySet()) {
                if (e.getKey().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_").equals(name) && e.getValue() != null && !e.getValue().isBlank()) {
                    return e.getValue();
                }
            }
        }
        return "";
    }

    private Integer parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.replace(",", "").replace("₹", "").trim();
        Matcher m = Pattern.compile("\\((.*)\\)").matcher(cleaned);
        if (m.find()) cleaned = "-" + m.group(1);
        try {
            return (int) Math.round(Double.parseDouble(cleaned));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return java.time.Instant.parse(raw).toString();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        } catch (Exception ignored) {
        }
        Matcher m = Pattern.compile("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})$").matcher(raw);
        if (m.find()) {
            int dd = Integer.parseInt(m.group(1));
            int mm = Integer.parseInt(m.group(2));
            int yyyy = Integer.parseInt(m.group(3));
            if (yyyy < 100) yyyy += 2000;
            return LocalDate.of(yyyy, mm, dd).atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        }
        return null;
    }

    Map<String, Object> heuristic(Raw r) {
        String t = r.title().toLowerCase(Locale.ROOT);
        String key = "other";
        double conf = 0.45;
        if (r.amount() > 0) {
            if (t.matches(".*(salary|payroll|wages).*")) { key = "salary"; conf = 0.8; }
            else if (t.matches(".*(freelance|invoice|consult).*")) { key = "freelance"; conf = 0.7; }
            else if (t.contains("dividend")) { key = "dividend"; conf = 0.85; }
            else if (t.contains("interest")) { key = "interest"; conf = 0.85; }
            else { key = "other"; conf = 0.4; }
        } else if (t.matches(".*(rent|landlord).*")) { key = "rent"; conf = 0.85; }
        else if (t.matches(".*(bigbasket|dmart|grocery|blinkit|zepto|nature).*")) { key = "groceries"; conf = 0.82; }
        else if (t.matches(".*(swiggy|zomato|restaurant|cafe|coffee|dining|toit|truffles).*")) { key = "dining"; conf = 0.82; }
        else if (t.matches(".*(uber|ola|metro|fuel|hpcl|irctc).*")) { key = "transport"; conf = 0.8; }
        else if (t.matches(".*(bescom|airtel|jio|electric|broadband|wifi).*")) { key = "utilities"; conf = 0.88; }
        else if (t.matches(".*(amazon|myntra|flipkart|ikea|decathlon).*")) { key = "shopping"; conf = 0.78; }
        else if (t.matches(".*(apollo|pharmacy|hospital|clinic|dr\\.).*")) { key = "health"; conf = 0.8; }
        else if (t.matches(".*(netflix|spotify|pvr|bookmyshow).*")) { key = "entertainment"; conf = 0.9; }
        else if (t.matches(".*(emi|loan).*")) { key = "emi"; conf = 0.9; }
        else if (t.matches(".*(sip|mutual fund|zerodha|groww|nps).*")) { key = "investment"; conf = 0.88; }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", r.title());
        m.put("amount", r.amount());
        m.put("date", r.date());
        m.put("category_key", key);
        m.put("note", r.note());
        m.put("source", "statement");
        m.put("confidence", conf);
        return m;
    }

    private List<Map<String, Object>> classify(List<Raw> rows) {
        List<Map<String, Object>> heuristic = rows.stream().map(this::heuristic).toList();
        String apiKey = props.getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return heuristic;
        }
        try {
            List<Map<String, Object>> payloadRows = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Raw r = rows.get(i);
                payloadRows.add(Map.of("index", i, "title", r.title(), "amount", r.amount(), "date", r.date()));
            }
            Map<String, Object> body = Map.of(
                    "model", props.getOpenai().getModel(),
                    "temperature", 0,
                    "response_format", Map.of("type", "json_object"),
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "Classify personal finance transactions in INR. Return JSON { items: [{ index, category_key, confidence }] }. category_key must be one of: "
                                            + String.join(", ", CATEGORY_KEYS)
                                            + ". confidence is 0..1. Positive amount is credit/income, negative is debit/expense."),
                            Map.of("role", "user", "content", mapper.writeValueAsString(payloadRows))
                    )
            );
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(props.getOpenai().getBaseUrl().replaceAll("/$", "") + "/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) return heuristic;
            JsonNode root = mapper.readTree(res.body());
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null) return heuristic;
            JsonNode items = mapper.readTree(content).path("items");
            Map<Integer, JsonNode> byIndex = new LinkedHashMap<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    byIndex.put(item.path("index").asInt(), item);
                }
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < heuristic.size(); i++) {
                Map<String, Object> row = new LinkedHashMap<>(heuristic.get(i));
                JsonNode llm = byIndex.get(i);
                if (llm != null && CATEGORY_KEYS.contains(llm.path("category_key").asText())) {
                    row.put("category_key", llm.path("category_key").asText());
                    double conf = llm.path("confidence").asDouble(0.7);
                    row.put("confidence", Math.min(1, Math.max(0, conf)));
                }
                out.add(row);
            }
            return out;
        } catch (Exception e) {
            return heuristic;
        }
    }
}
