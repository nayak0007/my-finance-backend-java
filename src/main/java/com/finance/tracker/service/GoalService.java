package com.finance.tracker.service;

import com.finance.tracker.domain.Goal;
import com.finance.tracker.dto.GoalDtos;
import com.finance.tracker.exception.AppException;
import com.finance.tracker.repository.GoalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GoalService {
    private static final Logger log = LoggerFactory.getLogger(GoalService.class);
    private final GoalRepository goals;

    public GoalService(GoalRepository goals) {
        this.goals = goals;
    }

    public List<Map<String, Object>> list(UUID userId) {
        List<Map<String, Object>> data = goals.findByUserId(userId).stream().map(this::toMap).toList();
        log.debug("goals list user={} count={}", userId, data.size());
        return data;
    }

    @Transactional
    public Map<String, Object> create(UUID userId, GoalDtos.CreateGoalRequest req) {
        log.info("goals create user={} name={} target={}", userId, req.name(), req.target());
        Goal g = new Goal();
        g.setUserId(userId);
        g.setName(req.name());
        g.setTarget(req.target());
        g.setSaved(req.saved() == null ? 0 : req.saved());
        g.setDueDate(LocalDate.parse(req.dueDate()));
        g.setColor(req.color() == null || req.color().isBlank() ? "#4F46E5" : req.color());
        Map<String, Object> created = toMap(goals.save(g));
        log.info("goals create ok user={} id={}", userId, created.get("id"));
        return created;
    }

    @Transactional
    public Map<String, Object> update(UUID userId, UUID id, GoalDtos.UpdateGoalRequest req) {
        log.info("goals update user={} id={}", userId, id);
        Goal g = goals.findByUserIdAndId(userId, id).orElseThrow(() -> AppException.notFound("Goal not found"));
        if (req.name() != null) g.setName(req.name());
        if (req.target() != null) g.setTarget(req.target());
        if (req.saved() != null) g.setSaved(req.saved());
        if (req.dueDate() != null) g.setDueDate(LocalDate.parse(req.dueDate()));
        if (req.color() != null) g.setColor(req.color());
        return toMap(goals.save(g));
    }

    private Map<String, Object> toMap(Goal g) {
        double pct = g.getTarget() > 0 ? Math.round((g.getSaved() / (double) g.getTarget()) * 1000.0) / 10.0 : 0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("user_id", g.getUserId());
        m.put("name", g.getName());
        m.put("target", g.getTarget());
        m.put("saved", g.getSaved());
        m.put("due_date", g.getDueDate());
        m.put("color", g.getColor());
        m.put("progress_pct", pct);
        return m;
    }
}
