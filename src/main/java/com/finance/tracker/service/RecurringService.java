package com.finance.tracker.service;

import com.finance.tracker.repository.RecurringRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RecurringService {
    private static final Logger log = LoggerFactory.getLogger(RecurringService.class);
    private final RecurringRepository recurring;

    public RecurringService(RecurringRepository recurring) {
        this.recurring = recurring;
    }

    public List<Map<String, Object>> list(UUID userId) {
        List<Map<String, Object>> data = recurring.findByUserId(userId).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("user_id", r.getUserId());
            m.put("name", r.getName());
            m.put("amount", r.getAmount());
            m.put("cycle", r.getCycle());
            m.put("next_date", r.getNextDate());
            m.put("change", r.getChange());
            m.put("icon", r.getIcon());
            return m;
        }).toList();
        log.debug("recurring list user={} count={}", userId, data.size());
        return data;
    }
}
