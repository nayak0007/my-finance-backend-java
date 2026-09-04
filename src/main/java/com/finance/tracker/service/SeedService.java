package com.finance.tracker.service;

import com.finance.tracker.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class SeedService {
    private static final Logger log = LoggerFactory.getLogger(SeedService.class);
    private static final UUID SALARY = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SAVINGS = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID FD = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID WALLET = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final JdbcTemplate jdbc;
    private final AppProperties props;

    public SeedService(JdbcTemplate jdbc, AppProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Transactional
    public void seed() {
        UUID userId = UUID.fromString(props.getSeedUserId());
        jdbc.execute("SELECT set_config('app.user_id', '" + userId + "', true)");
        Timestamp memberSince = ts(Instant.now().minus(400, ChronoUnit.DAYS));
        jdbc.update("""
                INSERT INTO profiles (id, name, email, plan, member_since)
                VALUES (?, 'Aarav Sharma', 'aarav.sharma@example.com', 'Plus', ?)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, email = EXCLUDED.email, plan = EXCLUDED.plan
                """, userId, memberSince);
        jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM accounts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM holdings WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM investment_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM recurring WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM goals WHERE user_id = ?", userId);

        insertAccount(SALARY, userId, "HDFC Bank", "Salary Account", "4821", "Salary", 248500, "4.20", "#2563EB", 185000, 97240, "connected");
        insertAccount(SAVINGS, userId, "SBI", "Savings", "9034", "Savings", 86420, "1.10", "#059669", 22000, 18450, "connected");
        insertAccount(FD, userId, "ICICI Bank", "Fixed Deposit", "1170", "FD", 500000, "0.55", "#D97706", 2750, 0, "connected");
        insertAccount(WALLET, userId, "Paytm", "Wallet", "2298", "Wallet", 2340, "-12.40", "#DB2777", 5000, 7660, "attention");

        Object[][] txns = {
                {SALARY, "salary", "Monthly salary — Acme Labs", 185000, 8, "email", "0.990"},
                {SALARY, "freelance", "Design sprint — Northwind", 42000, 21, "manual", null},
                {SAVINGS, "interest", "SB interest credit", 420, 12, "sms", "0.870"},
                {FD, "interest", "FD interest — ICICI", 2750, 18, "email", "0.940"},
                {SALARY, "dividend", "Nifty 50 ETF dividend", 1860, 35, "investment", "0.910"},
                {SALARY, "rent", "House rent — Koramangala", -32000, 6, "manual", null},
                {SALARY, "emi", "Home loan EMI — HDFC", -28450, 4, "sms", "0.980"},
                {SALARY, "groceries", "BigBasket weekly", -4280, 2, "email", "0.930"},
                {SALARY, "dining", "Toit Brewpub", -2480, 1, "sms", "0.920"},
                {SALARY, "investment", "Nifty 50 Index SIP", -10000, 5, "investment", "0.990"},
                {SALARY, "entertainment", "Netflix", -649, 13, "email", "0.990"},
                {WALLET, "transport", "Uber rides", -1260, 2, "email", "0.910"},
                {SALARY, "utilities", "Airtel broadband", -999, 10, "email", "0.990"},
                {SALARY, "shopping", "Amazon.in", -4599, 7, "email", "0.840"},
                {SALARY, "salary", "Monthly salary — Acme Labs", 185000, 38, "email", "0.990"},
                {SALARY, "rent", "House rent — Koramangala", -32000, 36, "manual", null},
                {SALARY, "emi", "Home loan EMI — HDFC", -28450, 34, "sms", "0.980"},
                {SALARY, "investment", "Nifty 50 Index SIP", -10000, 35, "investment", "0.990"},
                {SALARY, "salary", "Monthly salary — Acme Labs", 185000, 68, "email", "0.990"},
                {SALARY, "rent", "House rent — Koramangala", -32000, 66, "manual", null},
                {SALARY, "groceries", "DMart", -5640, 16, "statement", "0.860"},
                {SALARY, "dining", "Swiggy — Pizza", -890, 5, "email", "0.950"},
        };
        for (Object[] t : txns) {
            jdbc.update("""
                    INSERT INTO transactions (user_id, account_id, category_key, title, amount, date, source, confidence)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::numeric)
                     """, userId, t[0], t[1], t[2], t[3], ts(daysAgo((int) t[4])), t[5], t[6]);
        }

        jdbc.update("INSERT INTO holdings (user_id, name, issuer, klass, value, invested, xirr, color) VALUES (?,?,?,'Equity',?,?,?,?)",
                userId, "Nifty 50 Index Fund", "UTI", 186400, 140000, "16.40", "#2563EB");
        jdbc.update("INSERT INTO holdings (user_id, name, issuer, klass, value, invested, xirr, color) VALUES (?,?,?,'Equity',?,?,?,?)",
                userId, "Parag Parikh Flexi Cap", "PPFAS", 124800, 96000, "18.20", "#7C3AED");
        jdbc.update("INSERT INTO holdings (user_id, name, issuer, klass, value, invested, xirr, color) VALUES (?,?,?,'Gold',?,?,?,?)",
                userId, "Gold ETF", "Nippon", 48200, 40000, "11.10", "#D97706");
        jdbc.update("INSERT INTO holdings (user_id, name, issuer, klass, value, invested, xirr, color) VALUES (?,?,?,'Debt',?,?,?,?)",
                userId, "Corporate Bond Fund", "ICICI Pru", 76400, 72000, "7.80", "#059669");
        jdbc.update("INSERT INTO holdings (user_id, name, issuer, klass, value, invested, xirr, color) VALUES (?,?,?,'Cash & FD',?,?,?,?)",
                userId, "HDFC Bank FD", "HDFC Bank", 500000, 500000, "6.60", "#64748B");

        jdbc.update("INSERT INTO investment_history (user_id, date, title, type, amount) VALUES (?,?,?,'SIP',?)", userId, ts(daysAgo(5)), "Nifty 50 Index SIP", -10000);
        jdbc.update("INSERT INTO investment_history (user_id, date, title, type, amount) VALUES (?,?,?,'CREDIT',?)", userId, ts(daysAgo(18)), "FD interest — ICICI", 2750);
        jdbc.update("INSERT INTO investment_history (user_id, date, title, type, amount) VALUES (?,?,?,'BUY',?)", userId, ts(daysAgo(42)), "Gold ETF lumpsum", -15000);

        jdbc.update("INSERT INTO recurring (user_id, name, amount, cycle, next_date, change, icon) VALUES (?,?,?,'Monthly',?,?,?)",
                userId, "House rent", -32000, LocalDate.now().plusDays(24), "0%", "home");
        jdbc.update("INSERT INTO recurring (user_id, name, amount, cycle, next_date, change, icon) VALUES (?,?,?,'Monthly',?,?,?)",
                userId, "Netflix", -649, LocalDate.now().plusDays(17), "+8%", "tv");
        jdbc.update("INSERT INTO recurring (user_id, name, amount, cycle, next_date, change, icon) VALUES (?,?,?,'Monthly',?,?,?)",
                userId, "Home loan EMI", -28450, LocalDate.now().plusDays(26), "0%", "landmark");

        jdbc.update("INSERT INTO goals (user_id, name, target, saved, due_date, color) VALUES (?,?,?,?,?,?)",
                userId, "Emergency fund", 600000, 248500, LocalDate.parse("2027-03-31"), "#2563EB");
        jdbc.update("INSERT INTO goals (user_id, name, target, saved, due_date, color) VALUES (?,?,?,?,?,?)",
                userId, "Goa trip", 80000, 42000, LocalDate.parse("2026-12-15"), "#DB2777");
        jdbc.update("INSERT INTO goals (user_id, name, target, saved, due_date, color) VALUES (?,?,?,?,?,?)",
                userId, "MacBook Pro", 220000, 65000, LocalDate.parse("2027-06-01"), "#7C3AED");

        jdbc.execute("SELECT refresh_monthly_summary()");
        log.info("Seeded demo data for user {}", userId);
    }

    private void insertAccount(UUID id, UUID userId, String bank, String name, String mask, String type,
                               int balance, String change, String color, int inflow, int outflow, String status) {
        jdbc.update("""
                INSERT INTO accounts (id, user_id, bank, name, mask, type, balance, change_pct, color, inflow, outflow, status, last_synced_at)
                VALUES (?, ?, ?, ?, ?, ?::account_type, ?, ?::numeric, ?, ?, ?, ?::account_status, now())
                """, id, userId, bank, name, mask, type, balance, change, color, inflow, outflow, status);
    }

    private Instant daysAgo(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }
}
