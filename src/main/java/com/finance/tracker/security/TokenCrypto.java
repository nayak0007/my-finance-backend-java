package com.finance.tracker.security;

import com.finance.tracker.config.AppProperties;
import com.finance.tracker.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256/GCM encryption for third-party OAuth tokens stored at rest
 * (currently Google refresh tokens for Gmail sync).
 *
 * The key is derived from `app.sync.encryption-key` (env SYNC_ENCRYPTION_KEY);
 * when that is unset it falls back to NEON_JWT_SECRET so local setups work out
 * of the box. If neither is configured, Gmail sync refuses to start instead of
 * silently storing tokens in plaintext.
 */
@Component
public class TokenCrypto {

    private static final Logger log = LoggerFactory.getLogger(TokenCrypto.class);
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public TokenCrypto(AppProperties props) {
        this.key = deriveKey(props);
    }

    private byte[] deriveKey(AppProperties props) {
        String secret = props.getSync().getEncryptionKey();
        if (secret == null || secret.isBlank()) {
            secret = props.getNeon().getJwtSecret();
        }
        if (secret == null || secret.isBlank()) {
            log.warn("SYNC_ENCRYPTION_KEY / NEON_JWT_SECRET not set — Gmail sync will be unavailable until one is configured");
            // Random per-boot key: tokens cannot be decrypted after restart, so the
            // service fails closed until an operator sets a stable secret.
            byte[] randomKey = new byte[32];
            random.nextBytes(randomKey);
            return randomKey;
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return sha.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive token encryption key", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw AppException.upstream("Failed to encrypt sync tokens");
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) {
                return null;
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Could not decrypt sync token: {}", e.getMessage());
            return null;
        }
    }
}