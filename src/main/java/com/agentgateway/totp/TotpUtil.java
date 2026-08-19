package com.agentgateway.totp;

import org.apache.commons.codec.binary.Base32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;

/** RFC 6238 TOTP（HMAC-SHA1，30 秒步长，6 位） */
public final class TotpUtil {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    /** 生成 32 字符（160 位）Base32 密钥 */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        String b32 = new String(new Base32().encode(bytes), StandardCharsets.UTF_8);
        return b32.replace("=", "");
    }

    /** 生成当前步的 6 位验证码 */
    public static String generateCode(String secretBase32, Instant time) {
        long step = time.getEpochSecond() / TIME_STEP_SECONDS;
        return generateCode(secretBase32, step);
    }

    public static String generateCode(String secretBase32, long timeStep) {
        byte[] key = new Base32().decode(secretBase32.toUpperCase());
        byte[] msg = new byte[8];
        for (int i = 7; i >= 0; i--) {
            msg[i] = (byte) (timeStep & 0xFF);
            timeStep >>= 8;
        }
        byte[] hash = hmacSha1(key, msg);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", otp);
    }

    /** 校验，容忍 ±window 步的时钟漂移 */
    public static boolean verify(String secretBase32, String code, int window, Instant now) {
        long step = now.getEpochSecond() / TIME_STEP_SECONDS;
        for (int i = -window; i <= window; i++) {
            if (generateCode(secretBase32, step + i).equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 生成 otpauth:// URI，供二维码扫描 */
    public static String provisioningUri(String secretBase32, String issuer, String account) {
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8);
        String iss = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secretBase32
                + "&issuer=" + iss + "&algorithm=SHA1&digits=6&period=30";
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
