package com.finance.tracker.security;

import com.finance.tracker.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenCryptoTest {

    @Test
    void encryptsAndDecrypts() {
        AppProperties props = new AppProperties();
        props.getSync().setEncryptionKey("test-secret-key");
        TokenCrypto crypto = new TokenCrypto(props);

        String plain = "1//0gma1-refresh-token-abc123";
        String stored = crypto.encrypt(plain);
        assertNotEquals(plain, stored);
        assertEquals(plain, crypto.decrypt(stored));
    }

    @Test
    void blankValuesRoundTripAsNull() {
        AppProperties props = new AppProperties();
        props.getSync().setEncryptionKey("test-secret-key");
        TokenCrypto crypto = new TokenCrypto(props);
        assertNull(crypto.encrypt(null));
        assertNull(crypto.encrypt("  "));
        assertNull(crypto.decrypt(null));
    }

    @Test
    void wrongKeyFailsClosed() {
        AppProperties a = new AppProperties();
        a.getSync().setEncryptionKey("key-one");
        AppProperties b = new AppProperties();
        b.getSync().setEncryptionKey("key-two");
        TokenCrypto ca = new TokenCrypto(a);
        TokenCrypto cb = new TokenCrypto(b);
        String stored = ca.encrypt("secret-token");
        assertNull(cb.decrypt(stored));
    }
}