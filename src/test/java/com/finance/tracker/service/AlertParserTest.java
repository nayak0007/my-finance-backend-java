package com.finance.tracker.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertParserTest {

    @Test
    void parsesUpiDebitSms() {
        Optional<AlertParser.AlertTxn> r = AlertParser.parse(
                "HDFCBK",
                "Rs.1,250.00 debited from HDFC Bank A/C **4821 on 05-09-26 by VPA swiggy@ybl. UPI: 412345678901. Ref 221234567890.",
                null);
        assertTrue(r.isPresent());
        assertEquals(-1250, r.get().amount());
        assertEquals("swiggy@ybl", r.get().title());
        assertEquals("2026-09-05T00:00:00Z", r.get().date());
    }

    @Test
    void parsesCreditSms() {
        Optional<AlertParser.AlertTxn> r = AlertParser.parse(
                "HDFCBK",
                "HDFC Bank: Acct XX4821 credited INR 92,500.00 on 02-SEP-26 via NEFT. Ref 1234.",
                null);
        assertTrue(r.isPresent());
        assertEquals(92500, r.get().amount());
        assertEquals("2026-09-02T00:00:00Z", r.get().date());
    }

    @Test
    void parsesInfoFragmentAndSlashedDate() {
        Optional<AlertParser.AlertTxn> r = AlertParser.parse(
                "AD-SBIN",
                "Dear Customer, Rs 649.00 has been debited from your A/C XX1234 on 05/09/26 at 21:04. Info: NETFLIX.COM. If not done by you, call 1800.",
                null);
        assertTrue(r.isPresent());
        assertEquals(-649, r.get().amount());
        assertEquals("NETFLIX.COM", r.get().title());
        assertEquals("2026-09-05T00:00:00Z", r.get().date());
    }

    @Test
    void fallsBackToMessageTimestamp() {
        Optional<AlertParser.AlertTxn> r = AlertParser.parse(
                "ICICIB",
                "INR 1,860.00 debited from ICICI Bank A/C 1234. Spent at BESCOM via UPI.",
                "2026-08-27T09:15:00Z");
        assertTrue(r.isPresent());
        assertEquals(-1860, r.get().amount());
        assertTrue(r.get().title().contains("BESCOM"));
        assertEquals("2026-08-27T09:15:00Z", r.get().date());
    }

    @Test
    void ignoresNonFinancialSms() {
        assertFalse(AlertParser.parse("VM-PAYTM", "Your OTP for Paytm login is 482913. Do not share it with anyone.", null).isPresent());
        assertFalse(AlertParser.parse("VM-ICICI", "Happy birthday! Get 10% off on movie tickets.", null).isPresent());
    }

    @Test
    void parsesGmailHtmlBody() {
        String html = "<html><body><p>Your card ending 1234 was used for a purchase of <b>INR 2,340.00</b> at Amazon.in on 12-Aug-26.</p></body></html>";
        Optional<AlertParser.AlertTxn> r = AlertParser.parse("alerts@amazon.in", html, null);
        assertTrue(r.isPresent());
        assertEquals(-2340, r.get().amount());
        assertTrue(r.get().title().toLowerCase().contains("amazon"));
        assertEquals("2026-08-12T00:00:00Z", r.get().date());
    }
}