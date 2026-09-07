package com.finance.tracker;

import com.finance.tracker.service.SeedService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired SeedService seedService;

    private String token;
    private String otherToken;

    @BeforeAll
    void seed() {
        seedService.seed();
        token = JwtTokens.token(JwtTokens.USER, "aarav.sharma@example.com");
        otherToken = JwtTokens.token(JwtTokens.OTHER, "other@example.com");
    }

    @Test
    void health() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void meRequiresAuth() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void meWithValidJwt() throws Exception {
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(JwtTokens.USER.toString()))
                .andExpect(jsonPath("$.profile.email").value("aarav.sharma@example.com"));
    }

    @Test
    void oauthUrl() throws Exception {
        mvc.perform(get("/auth/oauth/google").param("redirect_to", "http://localhost:8081"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", containsString("/auth/v1/authorize")));
    }

    @Test
    void forgotPasswordAlwaysOk() throws Exception {
        // Always 200 ok regardless of whether the account exists or Supabase is reachable,
        // so the endpoint cannot be used to enumerate registered emails.
        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"aarav.sharma@example.com\",\"redirect_url\":\"myfinancetracker://reset-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@nowhere.invalid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void forgotPasswordRejectsBadEmail() throws Exception {
        mvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void resetPasswordRequiresValidToken() throws Exception {
        mvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"new-pass-1234\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/reset-password")
                        .header("Authorization", "Bearer not-a-real-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"new-pass-1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(post("/auth/reset-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void transactionsRequireAuth() throws Exception {
        mvc.perform(get("/api/v1/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void listTransactionsNewestFirst() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("limit", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.next_cursor").isNotEmpty());
    }

    @Test
    void transactionCrud() throws Exception {
        String body = """
                {"account_id":"11111111-1111-4111-8111-111111111111","category_key":"dining","title":"Test espresso","amount":-180,"date":"%s","source":"manual"}
                """.formatted(Instant.now().toString());
        String created = mvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test espresso"))
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(patch("/api/v1/transactions/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-220,\"note\":\"tipped\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(-220));

        mvc.perform(delete("/api/v1/transactions/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void doesNotLeakOtherUser() throws Exception {
        mvc.perform(get("/api/v1/transactions").param("limit", "20")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void validateCreate() throws Exception {
        mvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void accountsAndGoals() throws Exception {
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(4))));

        mvc.perform(post("/api/v1/goals")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New bike\",\"target\":90000,\"saved\":10000,\"due_date\":\"2027-01-15\",\"color\":\"#0EA5E9\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.progress_pct").isNumber());
    }

    @Test
    void insightsAndSummary() throws Exception {
        mvc.perform(get("/api/v1/insights").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].kind", hasItem("savings")));
        mvc.perform(get("/api/v1/summary/monthly").param("months", "12")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].income").exists());
    }

    @Test
    void parseCsv() throws Exception {
        String csv = "Date,Description,Amount\n2026-08-01,BigBasket weekly,-4280\n2026-08-02,Netflix,-649\n";
        mvc.perform(multipart("/api/v1/import/parse")
                        .file("file", csv.getBytes())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].category_key").isNotEmpty());
    }

    @Test
    void deleteAccountWithoutForceConflicts() throws Exception {
        mvc.perform(delete("/api/v1/accounts/" + UUID.fromString("11111111-1111-4111-8111-111111111111"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }
}
