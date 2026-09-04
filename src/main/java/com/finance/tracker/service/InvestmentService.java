package com.finance.tracker.service;

import com.finance.tracker.repository.HoldingRepository;
import com.finance.tracker.repository.InvestmentHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvestmentService {
    private final HoldingRepository holdings;
    private final InvestmentHistoryRepository history;
    private final SummaryService summaries;

    public InvestmentService(HoldingRepository holdings, InvestmentHistoryRepository history, SummaryService summaries) {
        this.holdings = holdings;
        this.history = history;
        this.summaries = summaries;
    }

    public List<Map<String, Object>> holdings(UUID userId) {
        return holdings.findByUserId(userId).stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("user_id", h.getUserId());
            m.put("name", h.getName());
            m.put("issuer", h.getIssuer());
            m.put("klass", h.getKlass());
            m.put("value", h.getValue());
            m.put("invested", h.getInvested());
            m.put("xirr", h.getXirr());
            m.put("color", h.getColor());
            m.put("gain", h.getValue() - h.getInvested());
            return m;
        }).toList();
    }

    public List<Map<String, Object>> history(UUID userId) {
        return history.findByUserIdOrderByDateDesc(userId).stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("user_id", h.getUserId());
            m.put("date", h.getDate());
            m.put("title", h.getTitle());
            m.put("type", h.getType());
            m.put("amount", h.getAmount());
            return m;
        }).toList();
    }

    public List<Map<String, Object>> growth(UUID userId, int months) {
        return summaries.investmentGrowth(userId, months);
    }
}
