package com.finance.tracker.controller;

import com.finance.tracker.exception.AppException;
import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.service.ImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/import")
public class ImportController {
    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestPart("file") MultipartFile file) throws Exception {
        AuthSupport.current();
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("Upload a statement file as multipart field `file`");
        }
        return Map.of("data", imports.parseFile(file.getBytes(), file.getOriginalFilename(), file.getContentType()));
    }
}
