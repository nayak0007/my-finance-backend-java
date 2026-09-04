package com.finance.tracker.util;

import java.text.NumberFormat;
import java.util.Locale;

public final class Money {
    private Money() {
    }

    public static String formatInr(int n) {
        return "₹" + NumberFormat.getIntegerInstance(new Locale("en", "IN")).format(Math.round((double) n));
    }

    public static String monthLabel(String ym) {
        try {
            String[] parts = ym.split("-");
            int y = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return java.time.YearMonth.of(y, m)
                    .atDay(1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH));
        } catch (Exception e) {
            return ym;
        }
    }
}
