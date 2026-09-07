package com.finance.tracker.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(DataSourceProperties properties) {
        JdbcUrls.Parsed parsed = JdbcUrls.parse(properties.getUrl(), properties.getUsername(), properties.getPassword());
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(parsed.jdbcUrl());
        ds.setDriverClassName("org.postgresql.Driver");
        if (parsed.user() != null) {
            ds.setUsername(parsed.user());
        }
        if (parsed.password() != null) {
            ds.setPassword(parsed.password());
        }
        return ds;
    }
}
