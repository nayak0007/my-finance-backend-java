package com.finance.tracker.service;

import com.finance.tracker.util.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SummaryService {

    private final JdbcTemplate jdbc;

    public SummaryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> monthly(UUID userId, int months) {
        return jdbc.query("""
                SELECT to_char(month, 'YYYY-MM') AS month, income, spending, invested
                FROM monthly_summary_view
                WHERE user_id = ?
                  AND month >= (date_trunc('month', now()) - (? || ' months')::interval)::date
                ORDER BY month ASC
                """, (rs, i) -> {
            String month = rs.getString("month");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", Money.monthLabel(month));
            m.put("month", month);
            m.put("income", rs.getInt("income"));
            m.put("spending", rs.getInt("spending"));
            m.put("invested", rs.getInt("invested"));
            return m;
        }, userId, months - 1);
    }

    public Map<String, Object> spendingByCategory(UUID userId, String month) {
        List<Map<String, Object>> data = jdbc.query("""
                WITH bounds AS (
                  SELECT (? || '-01')::date AS start_month,
                         ((? || '-01')::date - interval '1 month')::date AS prev_month
                )
                SELECT c.key, c.label,
                  COALESCE(SUM(CASE WHEN date_trunc('month', t.date)::date = b.start_month THEN ABS(t.amount) ELSE 0 END), 0)::integer AS amount,
                  COALESCE(SUM(CASE WHEN date_trunc('month', t.date)::date = b.prev_month THEN ABS(t.amount) ELSE 0 END), 0)::integer AS previous_amount
                FROM categories c
                CROSS JOIN bounds b
                LEFT JOIN transactions t
                  ON t.category_key = c.key AND t.user_id = ? AND t.amount < 0 AND c.kind = 'expense'
                  AND date_trunc('month', t.date)::date IN (b.start_month, b.prev_month)
                WHERE c.kind = 'expense'
                GROUP BY c.key, c.label
                HAVING COALESCE(SUM(CASE WHEN date_trunc('month', t.date)::date = b.start_month THEN ABS(t.amount) ELSE 0 END), 0) > 0
                    OR COALESCE(SUM(CASE WHEN date_trunc('month', t.date)::date = b.prev_month THEN ABS(t.amount) ELSE 0 END), 0) > 0
                ORDER BY amount DESC
                """, (rs, i) -> {
            int amount = rs.getInt("amount");
            int prev = rs.getInt("previous_amount");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", rs.getString("key"));
            m.put("label", rs.getString("label"));
            m.put("amount", amount);
            m.put("previous_amount", prev);
            m.put("change_pct", prev > 0 ? Math.round(((amount - prev) / (double) prev) * 1000.0) / 10.0 : null);
            return m;
        }, month, month, userId);
        return Map.of("month", month, "data", data);
    }

    public List<Map<String, Object>> investmentGrowth(UUID userId, int months) {
        List<Map<String, Object>> rows = jdbc.query("""
                WITH months AS (
                  SELECT generate_series(
                    date_trunc('month', now()) - (? || ' months')::interval,
                    date_trunc('month', now()),
                    interval '1 month'
                  )::date AS month
                ),
                flows AS (
                  SELECT date_trunc('month', date)::date AS month,
                         SUM(CASE WHEN amount < 0 THEN ABS(amount) ELSE 0 END)::integer AS invested,
                         SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END)::integer AS credited
                  FROM investment_history
                  WHERE user_id = ?
                  GROUP BY date_trunc('month', date)::date
                )
                SELECT to_char(m.month, 'YYYY-MM') AS month,
                       COALESCE(f.invested, 0)::integer AS invested,
                       COALESCE(f.credited, 0)::integer AS credited
                FROM months m
                LEFT JOIN flows f ON f.month = m.month
                ORDER BY m.month ASC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", rs.getString("month"));
            m.put("invested", rs.getInt("invested"));
            m.put("credited", rs.getInt("credited"));
            return m;
        }, months - 1, userId);
        int cumulative = 0;
        for (Map<String, Object> row : rows) {
            int invested = (int) row.get("invested");
            int credited = (int) row.get("credited");
            cumulative += invested - credited;
            Map<String, Object> copy = new LinkedHashMap<>();
            copy.put("label", Money.monthLabel((String) row.get("month")));
            copy.put("month", row.get("month"));
            copy.put("invested", invested);
            copy.put("credited", credited);
            copy.put("cumulative", cumulative);
            row.clear();
            row.putAll(copy);
        }
        return rows;
    }

    public List<Map<String, Object>> overspend(UUID userId) {
        return jdbc.query("""
                WITH monthly AS (
                  SELECT t.category_key, c.label, date_trunc('month', t.date)::date AS month, SUM(ABS(t.amount))::integer AS total
                  FROM transactions t
                  JOIN categories c ON c.key = t.category_key
                  WHERE t.user_id = ? AND t.amount < 0 AND c.kind = 'expense'
                    AND t.category_key NOT IN ('emi', 'rent', 'investment')
                    AND t.date >= date_trunc('month', now()) - interval '6 months'
                  GROUP BY t.category_key, c.label, date_trunc('month', t.date)::date
                ),
                stats AS (
                  SELECT category_key, label,
                         AVG(total) FILTER (WHERE month < date_trunc('month', now())::date) AS avg6,
                         MAX(total) FILTER (WHERE month = date_trunc('month', now())::date) AS current
                  FROM monthly GROUP BY category_key, label
                )
                SELECT category_key, label, COALESCE(avg6, 0)::numeric AS avg6, COALESCE(current, 0)::integer AS current
                FROM stats WHERE current > avg6 * 1.2 AND current > 0
                ORDER BY (current - avg6) DESC LIMIT 5
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("category_key", rs.getString("category_key"));
            m.put("label", rs.getString("label"));
            m.put("avg6", rs.getBigDecimal("avg6"));
            m.put("current", rs.getInt("current"));
            return m;
        }, userId);
    }

    public List<Map<String, Object>> hikes(UUID userId) {
        return jdbc.query("""
                WITH ranked AS (
                  SELECT title, ABS(amount) AS amt, date,
                         LAG(ABS(amount)) OVER (PARTITION BY title ORDER BY date) AS prev_amt
                  FROM transactions
                  WHERE user_id = ? AND amount < 0 AND source IN ('email', 'sms')
                    AND category_key IN ('entertainment', 'utilities')
                )
                SELECT title, prev_amt, amt AS current_amt, date
                FROM ranked
                WHERE prev_amt IS NOT NULL AND amt > prev_amt AND amt - prev_amt >= 10
                ORDER BY date DESC LIMIT 5
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", rs.getString("title"));
            m.put("prev_amt", rs.getInt("prev_amt"));
            m.put("current_amt", rs.getInt("current_amt"));
            return m;
        }, userId);
    }

    public Map<String, Object> savings(UUID userId) {
        return jdbc.queryForObject("""
                SELECT
                  COALESCE(SUM(CASE WHEN amount > 0 THEN amount ELSE 0 END), 0)::integer AS income,
                  COALESCE(SUM(CASE WHEN amount < 0 AND category_key <> 'investment' THEN ABS(amount) ELSE 0 END), 0)::integer AS spending,
                  COALESCE(SUM(CASE WHEN category_key = 'investment' THEN ABS(amount) ELSE 0 END), 0)::integer AS invested
                FROM transactions
                WHERE user_id = ?
                  AND date >= date_trunc('month', now()) - interval '1 month'
                  AND date < date_trunc('month', now()) + interval '1 month'
                """, (rs, i) -> Map.of(
                "income", rs.getInt("income"),
                "spending", rs.getInt("spending"),
                "invested", rs.getInt("invested")
        ), userId);
    }

    public List<Map<String, Object>> recurringHikes(UUID userId) {
        return jdbc.query("""
                SELECT name, amount, change, cycle, next_date
                FROM recurring
                WHERE user_id = ? AND change IS NOT NULL AND change LIKE '+%'
                ORDER BY amount ASC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", rs.getString("name"));
            m.put("amount", rs.getInt("amount"));
            m.put("change", rs.getString("change"));
            m.put("cycle", rs.getString("cycle"));
            m.put("next_date", rs.getString("next_date"));
            return m;
        }, userId);
    }
}
