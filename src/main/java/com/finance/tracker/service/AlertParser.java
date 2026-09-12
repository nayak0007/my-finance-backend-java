package com.finance.tracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a single transaction from a free-form bank alert — the kind of
 * message a bank sends over SMS or email:
 *
 * <pre>
 *   "Rs.1,250.00 debited from HDFC Bank A/C **4821 on 05-09-26 by VPA swiggy@ybl."
 *   "HDFC Bank: Acct XX4821 credited INR 92,500.00 on 02-SEP-26 via NEFT. Ref 1234."
 *   "Dear Customer, Rs 649.00 has been debited from your A/C XX1234 on 05/09/26. Info: NETFLIX.COM"
 * </pre>
 *
 * Amount + direction come from currency-prefixed numbers and debit/credit
 * keywords; the date accepts the common Indian alert formats and falls back to
 * the message timestamp; the title is the merchant / VPA / Info fragment or the
 * sender. Returns an empty result when the message is not financial.
 */
public final class AlertParser {

    private static final Logger log = LoggerFactory.getLogger(AlertParser.class);

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_DMY_PATTERN = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b");
    private static final Pattern DATE_D_MON_Y_PATTERN = Pattern.compile("\\b(\\d{1,2})[- ]([A-Za-z]{3})[- ](\\d{2,4})\\b");

    private static final Pattern CREDIT_PATTERN = Pattern.compile(
            "\\b(credited|credit of|received|refund|refunded|added to|deposited|interest paid|payment received|reversed)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBIT_PATTERN = Pattern.compile(
            "\\b(debited|debit of|spent|paid|purchase|withdrawal|withdrawn|autopay|emi debited|recurring payment|transaction of)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INFO_PATTERN = Pattern.compile(
            "\\binfo\\s*[: ]\\s*([A-Za-z0-9][A-Za-z0-9 @._&#'-]{2,60})", Pattern.CASE_INSENSITIVE);
    private static final Pattern VPA_PATTERN = Pattern.compile(
            "\\b(?:vpa|via)\\s*([\\w.-]+@[\\w.-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MERCHANT_PATTERN = Pattern.compile(
            "\\b(?:at|via|towards|to)\\s+([A-Za-z][A-Za-z0-9 @._-]{2,40})", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter MONTH_FMT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM")
            .toFormatter(Locale.ENGLISH);

    private AlertParser() {
    }

    public record AlertTxn(String title, int amount, String date, String note) {}

    /** Parses one alert message; empty when no financial transaction was found. */
    public static Optional<AlertTxn> parse(String address, String body, String messageDate) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        String text = normalize(body);
        if (text.length() < 10) {
            return Optional.empty();
        }

        Integer amount = extractAmount(text);
        if (amount == null) {
            return Optional.empty();
        }

        boolean credit = CREDIT_PATTERN.matcher(text).find();
        boolean debit = DEBIT_PATTERN.matcher(text).find();
        int signed = (credit && !debit) ? Math.abs(amount) : -Math.abs(amount);

        String date = extractDate(text, messageDate);
        if (date == null) {
            return Optional.empty();
        }

        String title = extractTitle(text, address);
        if (title.isBlank()) {
            title = "Bank alert";
        }

        String note = text.length() > 300 ? text.substring(0, 300) : text;
        return Optional.of(new AlertTxn(title, signed, date, note));
    }

    /** Removes HTML tags and collapses whitespace (Gmail bodies are often HTML). */
    static String normalize(String body) {
        String noTags = body.replaceAll("(?s)<[^>]*>", " ");
        return noTags.replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Integer extractAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        String raw = m.group(1).replace(",", "").trim();
        try {
            return (int) Math.round(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String extractDate(String text, String messageDate) {
        Matcher dmy = DATE_DMY_PATTERN.matcher(text);
        if (dmy.find()) {
            LocalDate parsed = parseDmy(dmy.group(1), dmy.group(2), dmy.group(3));
            if (parsed != null) return atUtc(parsed);
        }
        Matcher dMonY = DATE_D_MON_Y_PATTERN.matcher(text);
        if (dMonY.find()) {
            LocalDate parsed = parseDMonY(dMonY.group(1), dMonY.group(2), dMonY.group(3));
            if (parsed != null) return atUtc(parsed);
        }
        if (messageDate != null && !messageDate.isBlank()) {
            try {
                return Instant.parse(messageDate).toString();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static LocalDate parseDmy(String dd, String mm, String yyyy) {
        try {
            int day = Integer.parseInt(dd);
            int month = Integer.parseInt(mm);
            int year = Integer.parseInt(yyyy);
            if (year < 100) year += 2000;
            if (month < 1 || month > 12 || day < 1 || day > 31) return null;
            LocalDate d = LocalDate.of(year, month, day);
            // Indian alerts are dd/mm/yyyy; if the first number is clearly a month
            // (>12) treat it as mm/dd instead.
            if (day > 12 && month <= 12) {
                return LocalDate.of(year, day, month);
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseDMonY(String dd, String mon, String yyyy) {
        try {
            int day = Integer.parseInt(dd);
            int month = MONTH_FMT.parse(mon.toUpperCase(Locale.ENGLISH)).get(java.time.temporal.ChronoField.MONTH_OF_YEAR);
            int year = Integer.parseInt(yyyy);
            if (year < 100) year += 2000;
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private static String atUtc(LocalDate d) {
        return d.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
    }

    private static String extractTitle(String text, String address) {
        Matcher info = INFO_PATTERN.matcher(text);
        if (info.find()) {
            String t = cleanTitle(info.group(1));
            if (!t.isBlank()) return t;
        }
        Matcher vpa = VPA_PATTERN.matcher(text);
        if (vpa.find()) {
            String t = cleanTitle(vpa.group(1));
            if (!t.isBlank()) return t;
        }
        Matcher merchant = MERCHANT_PATTERN.matcher(text);
        while (merchant.find()) {
            String candidate = merchant.group(1).trim();
            // Skip fragments that are really times ("at 21:04") or account refs.
            if (candidate.matches("^\\d{1,2}:\\d{2}$") || candidate.matches("^\\d+$")) {
                continue;
            }
            String t = cleanTitle(candidate);
            if (!t.isBlank()) return t;
        }
        if (address != null && !address.isBlank()) {
            String sender = address.contains("<") ? address.replaceAll(".*<([^>]+)>", "$1") : address;
            sender = sender.replaceAll("@.*$", "").replaceAll("[._-]+", " ").trim();
            if (!sender.isBlank() && !sender.matches("^\\d+$")) {
                String t = cleanTitle(sender);
                if (!t.isBlank()) return t;
            }
        }
        return "";
    }

    private static String cleanTitle(String raw) {
        String t = raw.trim();
        // Cut at the first sentence boundary (period/comma followed by a space)
        // so fragments like "NETFLIX.COM. If not done by you…" stop at the
        // sentence, while interior dots (NETFLIX.COM) are preserved.
        java.util.regex.Matcher boundary = Pattern.compile("[.,](?=\\s)").matcher(t);
        if (boundary.find()) {
            t = t.substring(0, boundary.start() + 1);
        }
        t = t.replaceAll("[.\\s]+$", "").replaceAll("^[^A-Za-z0-9]+", "");
        return t.length() > 60 ? t.substring(0, 60) : t;
    }
}