package com.finance.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Neon neon = new Neon();
    private final Openai openai = new Openai();
    private final Cors cors = new Cors();
    private final Auth auth = new Auth();
    private String seedUserId = "00000000-0000-4000-8000-000000000001";

    public Neon getNeon() {
        return neon;
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

    public static class Neon {
        private String authUrl = "";
        private String jwtSecret = "";

        public String getAuthUrl() {
            return authUrl;
        }

        public void setAuthUrl(String authUrl) {
            this.authUrl = authUrl;
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
         * Where the password-recovery email link sends the user after Neon Auth
         * verifies the one-time token (query carries token). When blank, the
         * client-supplied redirect_url is used.
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
