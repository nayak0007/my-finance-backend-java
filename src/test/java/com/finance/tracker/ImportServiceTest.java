package com.finance.tracker;

import com.finance.tracker.config.AppProperties;
import com.finance.tracker.service.ImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void parsesCsv() throws Exception {
        ImportService svc = new ImportService(new AppProperties(), new ObjectMapper());
        Method m = ImportService.class.getDeclaredMethod("parseStatementText", String.class);
        m.setAccessible(true);
        String csv = "Date,Description,Amount\n2026-08-01,BigBasket weekly,-4280\n2026-08-02,Salary credit,185000\n";
        List<?> rows = (List<?>) m.invoke(svc, csv);
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).toString().contains("BigBasket"));
    }
}
