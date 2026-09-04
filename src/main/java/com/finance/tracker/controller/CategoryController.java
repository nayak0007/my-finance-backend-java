package com.finance.tracker.controller;

import com.finance.tracker.repository.CategoryRepository;
import com.finance.tracker.security.AuthSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    @GetMapping
    public Map<String, Object> list() {
        AuthSupport.current();
        return Map.of("data", categories.findAll().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", c.getKey());
            m.put("label", c.getLabel());
            m.put("kind", c.getKind());
            return m;
        }).toList());
    }
}
