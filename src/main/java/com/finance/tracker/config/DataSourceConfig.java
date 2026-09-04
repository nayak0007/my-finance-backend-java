package com.finance.tracker.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties) {
        String url = properties.getUrl();
        String user = properties.getUsername();
        String password = properties.getPassword();
        if (url != null && (url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
            Parsed parsed = parseLibpq(url);
            url = parsed.jdbcUrl;
            if (user == null || user.isBlank()) user = parsed.user;
            if (password == null || password.isBlank()) password = parsed.password;
        }
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        if (url != null && url.startsWith("jdbc:postgresql://") && !url.contains("stringtype=")) {
            url = url + (url.contains("?") ? "&" : "?") + "stringtype=unspecified";
        }
        ds.setJdbcUrl(url);
        if (user != null) ds.setUsername(user);
        if (password != null) ds.setPassword(password);
        return ds;
    }

    private Parsed parseLibpq(String url) {
        URI uri = URI.create(url);
        String user = "";
        String password = "";
        if (uri.getUserInfo() != null) {
            String[] parts = uri.getUserInfo().split(":", 2);
            user = parts[0];
            password = parts.length > 1 ? parts[1] : "";
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String db = uri.getPath() == null || uri.getPath().isBlank() ? "/postgres" : uri.getPath();
        String query = uri.getQuery() == null ? "sslmode=prefer" : uri.getQuery();
        if (!query.contains("stringtype=")) {
            query = query + (query.isEmpty() ? "" : "&") + "stringtype=unspecified";
        }
        String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + db + (query.isEmpty() ? "" : "?" + query);
        return new Parsed(jdbc, user, password);
    }

    private record Parsed(String jdbcUrl, String user, String password) {}
}
