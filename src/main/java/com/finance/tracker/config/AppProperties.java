package com.finance.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Neon neon = new Neon();
    private final Openai openai = new Openai();
    private final Cors cors = new Cors();
    private final Auth auth = new Auth();
    private final Gmail gmail = new Gmail();
    private final Sync sync = new Sync();
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

    public Gmail getGmail() {
        return gmail;
    }

    public Sync getSync() {
        return sync;
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
        /**
         * Origin value sent on server-to-server requests to Neon Auth (Better Auth).
         * Better Auth rejects POSTs without an Origin header when the body carries no
         * absolute callbackURL (MISSING_ORIGIN). Should be one of the origins listed
         * under Neon Console → Auth → Trusted domains (e.g. the app's web origin).
         */
        private String authOrigin = "";

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

        public String getAuthOrigin() {
            return authOrigin;
        }

        public void setAuthOrigin(String authOrigin) {
            this.authOrigin = authOrigin;
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

    /**
     * Google OAuth client used for Gmail sync. Credentials live server-side;
     * the app only opens the consent URL and the backend callback receives the
     * code. Create a Google Cloud OAuth client of type "Web application" and
     * add the redirect URI (GOOGLE_REDIRECT_URI) to its authorized origins.
     */
    public static class Gmail {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";
        private String scopes = "https://www.googleapis.com/auth/gmail.readonly";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getScopes() {
            return scopes;
        }

        public void setScopes(String scopes) {
            this.scopes = scopes;
        }
    }

    public static class Sync {
        /**
         * Secret used to AES-encrypt Google refresh tokens at rest. Falls back
         * to NEON_JWT_SECRET when unset. Either must be configured for Gmail
         * sync to work.
         */
        private String encryptionKey = "";

        public String getEncryptionKey() {
            return encryptionKey;
        }

        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }
    }
}
