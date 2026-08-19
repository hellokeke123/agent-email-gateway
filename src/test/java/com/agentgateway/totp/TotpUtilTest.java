package com.agentgateway.totp;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpUtilTest {

    /** RFC 6238 Appendix B 测试向量：ASCII "12345678901234567890" 的 Base32 */
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void rfc6238Vectors() {
        // RFC 6238 Appendix B 向量：T 为 Unix 秒，内部换算为步长计数器 floor(T/30)
        assertEquals("287082", TotpUtil.generateCode(SECRET, Instant.ofEpochSecond(59L)));
        assertEquals("081804", TotpUtil.generateCode(SECRET, Instant.ofEpochSecond(1111111109L)));
        assertEquals("050471", TotpUtil.generateCode(SECRET, Instant.ofEpochSecond(1111111111L)));
        assertEquals("005924", TotpUtil.generateCode(SECRET, Instant.ofEpochSecond(1234567890L)));
        assertEquals("279037", TotpUtil.generateCode(SECRET, Instant.ofEpochSecond(2000000000L)));
        assertEquals("353130", TotpUtil.generateCode(SECRET, Instant.ofEpochSecond(20000000000L)));
    }

    @Test
    void verifyMatchesGeneratedCode() {
        String code = TotpUtil.generateCode(SECRET, Instant.now());
        assertTrue(TotpUtil.verify(SECRET, code, 1, Instant.now()));
        assertFalse(TotpUtil.verify(SECRET, "000000", 0, Instant.now()));
    }

    @Test
    void generatedSecretIsValidBase32() {
        String secret = TotpUtil.generateSecret();
        assertTrue(secret.length() >= 32);
        String code = TotpUtil.generateCode(secret, Instant.now());
        assertTrue(TotpUtil.verify(secret, code, 1, Instant.now()));
    }
}
