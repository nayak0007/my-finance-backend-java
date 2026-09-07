package com.finance.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Supabase supabase = new Supabase();
    private final Openai openai = new Openai();
    private final Cors cors = new Cors();
    private final Auth auth = new Auth();
    private String seedUserId = "00000000-0000-4000-8000-000000000001";

    public Supabase getSupabase() {
        return supabase;
    }

    public Auth getAuth() {
        return auth;
    }

    public Openai getOpenai() {
        return openai;
    }

    public Cors getCors() {
        return cors;
    }

    public String getSeedUserId() {
        return seedUserId;
    }

    public void setSeedUserId(String seedUserId) {
        this.seedUserId = seedUserId;
    }

    public static class Supabase {
        private String url = "https://example.supabase.co";
        private String anonKey = "";
        private String serviceRoleKey = "";
        private String jwtSecret = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getAnonKey() {
            return anonKey;
        }

        public void setAnonKey(String anonKey) {
            this.anonKey = anonKey;
        }

        public String getServiceRoleKey() {
            return serviceRoleKey;
        }

        public void setServiceRoleKey(String serviceRoleKey) {
            this.serviceRoleKey = serviceRoleKey;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }
    }

    public static class Openai {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o-mini";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Auth {
        /**
         * Where the password-recovery email link sends the user after Supabase
         * verifies the one-time token (fragment carries access_token/type=recovery).
         * When blank, the client-supplied redirect_url (or Supabase's default site
         * URL) is used.
         */
        private String redirectUrl = "";

        public String getRedirectUrl() {
            return redirectUrl;
        }

        public void setRedirectUrl(String redirectUrl) {
            this.redirectUrl = redirectUrl;
        }
    }

    public static class Cors {
        private String origins = "http://localhost:8081";

        public String getOrigins() {
            return origins;
        }

        public void setOrigins(String origins) {
            this.origins = origins;
        }
    }
}
