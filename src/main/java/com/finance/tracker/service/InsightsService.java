package com.finance.tracker.service;

import com.finance.tracker.util.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InsightsService {

    private static final Logger log = LoggerFactory.getLogger(InsightsService.class);

    private final SummaryService summaries;

    public InsightsService(SummaryService summaries) {
        this.summaries = summaries;
    }

    public List<Map<String, Object>> compute(UUID userId) {
        log.debug("insights compute user={}", userId);
        List<Map<String, Object>> insights = new ArrayList<>();

        for (Map<String, Object> row : summaries.overspend(userId)) {
            int current = ((Number) row.get("current")).intValue();
            double avg6 = ((BigDecimal) row.get("avg6")).doubleValue();
            int pct = avg6 > 0 ? (int) Math.round(((current - avg6) / avg6) * 100) : 0;
            String label = String.valueOf(row.get("label"));
            Map<String, Object> insight = new LinkedHashMap<>();
            insight.put("id", "overspend-" + row.get("category_key"));
            insight.put("kind", "overspend");
            insight.put("title", label + " is " + pct + "% above your 6-month average");
            insight.put("body", "You spent " + Money.formatInr(current) + " on " + label.toLowerCase()
                    + " this month versus " + Money.formatInr((int) Math.round(avg6)) + " on average.");
            insight.put("severity", pct >= 40 ? "warn" : "info");
            insight.put("meta", Map.of("category_key", row.get("category_key"), "current", current, "avg6", avg6, "pct", pct));
            insights.add(insight);
        }

        for (Map<String, Object> row : summaries.hikes(userId)) {
            int prev = ((Number) row.get("prev_amt")).intValue();
            int current = ((Number) row.get("current_amt")).intValue();
            Map<String, Object> insight = new LinkedHashMap<>();
            insight.put("id", "hike-" + row.get("title") + "-" + current);
            insight.put("kind", "hike");
            insight.put("title", row.get("title") + " increased");
            insight.put("body", "Charge moved from " + Money.formatInr(prev) + " to " + Money.formatInr(current) + ".");
            insight.put("severity", "warn");
            insight.put("meta", Map.of("title", row.get("title"), "prev", prev, "current", current));
            insights.add(insight);
        }

        for (Map<String, Object> row : summaries.recurringHikes(userId)) {
            Map<String, Object> insight = new LinkedHashMap<>();
            insight.put("id", "sub-" + row.get("name"));
            insight.put("kind", "subscription");
            insight.put("title", row.get("name") + " price hike (" + row.get("change") + ")");
            insight.put("body", "Next " + String.valueOf(row.get("cycle")).toLowerCase() + " charge of "
                    + Money.formatInr(Math.abs(((Number) row.get("amount")).intValue()))
                    + " is due " + row.get("next_date") + ".");
            insight.put("severity", "warn");
            insight.put("meta", row);
            insights.add(insight);
        }

        Map<String, Object> s = summaries.savings(userId);
        int income = ((Number) s.get("income")).intValue();
        int spending = ((Number) s.get("spending")).intValue();
        int invested = ((Number) s.get("invested")).intValue();
        int saved = income - spending - invested;
        int rate = income > 0 ? (int) Math.round((saved / (double) income) * 100) : 0;
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("id", "savings-rate");
        insight.put("kind", "savings");
        insight.put("title", "Savings rate is " + rate + "% this period");
        insight.put("body", "Income " + Money.formatInr(income) + ", spending " + Money.formatInr(spending)
                + ", invested " + Money.formatInr(invested) + ". Net leftover " + Money.formatInr(saved) + ".");
        insight.put("severity", rate >= 20 ? "good" : rate >= 10 ? "info" : "warn");
        insight.put("meta", Map.of("income", income, "spending", spending, "invested", invested, "saved", saved, "rate", rate));
        insights.add(insight);
        log.debug("insights compute ok user={} count={}", userId, insights.size());
        return insights;
    }
}
