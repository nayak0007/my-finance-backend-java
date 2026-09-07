package com.finance.tracker.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class JdbcUrls {
    private JdbcUrls() {}

    public record Parsed(String jdbcUrl, String user, String password) {}

    public static Parsed parse(String url, String fallbackUser, String fallbackPassword) {
        if (url == null || url.isBlank()) {
            return new Parsed(url, fallbackUser, fallbackPassword);
        }
        String jdbc = url;
        String user = fallbackUser;
        String password = fallbackPassword;
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            URI uri = URI.create(url);
            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                String[] parts = uri.getUserInfo().split(":", 2);
                user = decode(parts[0]);
                password = parts.length > 1 ? decode(parts[1]) : "";
            }
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String db = uri.getPath() == null || uri.getPath().isBlank() ? "/postgres" : uri.getPath();
            String query = uri.getQuery() == null ? "" : uri.getQuery();
            jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + db + (query.isEmpty() ? "" : "?" + query);
        } else if (url.startsWith("jdbc:postgresql://")) {
            int at = url.indexOf('@');
            int schemeEnd = "jdbc:postgresql://".length();
            if (at > schemeEnd && url.indexOf('/', schemeEnd) > at) {
                String userInfo = url.substring(schemeEnd, at);
                int colon = userInfo.indexOf(':');
                if (colon >= 0) {
                    user = decode(userInfo.substring(0, colon));
                    password = decode(userInfo.substring(colon + 1));
                } else if (!userInfo.isBlank()) {
                    user = decode(userInfo);
                }
                jdbc = "jdbc:postgresql://" + url.substring(at + 1);
            }
        }
        jdbc = appendQuery(jdbc, "stringtype", "unspecified");
        if (!hasQueryParam(jdbc, "sslmode") && needsRequiredSsl(jdbc)) {
            jdbc = appendQuery(jdbc, "sslmode", "require");
        }
        return new Parsed(jdbc, user, password);
    }

    private static boolean needsRequiredSsl(String jdbc) {
        String host = hostOf(jdbc);
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase();
        if (h.equals("localhost") || h.equals("127.0.0.1") || h.equals("::1") || h.equals("[::1]")) {
            return false;
        }
        return h.contains(".");
    }

    private static String hostOf(String jdbc) {
        String rest = jdbc.substring("jdbc:postgresql://".length());
        int slash = rest.indexOf('/');
        int q = rest.indexOf('?');
        int end = rest.length();
        if (slash >= 0) end = Math.min(end, slash);
        if (q >= 0) end = Math.min(end, q);
        String hostPort = rest.substring(0, end);
        if (hostPort.startsWith("[")) {
            int close = hostPort.indexOf(']');
            return close > 0 ? hostPort.substring(0, close + 1) : hostPort;
        }
        int colon = hostPort.lastIndexOf(':');
        return colon > 0 ? hostPort.substring(0, colon) : hostPort;
    }

    private static boolean hasQueryParam(String jdbc, String key) {
        int q = jdbc.indexOf('?');
        if (q < 0) {
            return false;
        }
        String query = jdbc.substring(q + 1);
        for (String part : query.split("&")) {
            if (part.equals(key) || part.startsWith(key + "=")) {
                return true;
            }
        }
        return false;
    }

    private static String appendQuery(String jdbc, String key, String value) {
        if (hasQueryParam(jdbc, key)) {
            return jdbc;
        }
        return jdbc + (jdbc.contains("?") ? "&" : "?") + key + "=" + value;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
