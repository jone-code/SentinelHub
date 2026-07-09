package com.sentinelhub.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnCredentialGeneratorTest {

    @Test
    void hmacSha1Base64_matchesCoturnRestApiExample() {
        String credential = TurnCredentialGenerator.hmacSha1Base64("secret", "1440860400:someusername");
        assertEquals("jOfualJapC9yiZQwGriLkaqiUC0=", credential);
    }

    @Test
    void generate_buildsExpiryUsernameAndCredential() {
        TurnCredentialGenerator.TurnCredentials creds =
                TurnCredentialGenerator.generate("test-secret", 3600);

        assertTrue(creds.username().endsWith(":sentinel"));
        String expiryPart = creds.username().split(":")[0];
        assertTrue(Long.parseLong(expiryPart) > 0);
        assertTrue(creds.credential().length() > 10);
    }

    @Test
    void generate_rejectsBlankSecret() {
        assertThrows(IllegalArgumentException.class, () -> TurnCredentialGenerator.generate("  ", 3600));
    }
}
