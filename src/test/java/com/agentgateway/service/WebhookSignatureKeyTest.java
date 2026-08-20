package com.agentgateway.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 回归测试：锁定 webhook HMAC 签名的 key 必须用原始 signing secret，
 * 而不是它的 SHA-256 哈希。
 *
 * 当前（有 bug）此测试应为红：发送方用 webhookSigningSecretHash 作 key，
 * 与接收方用原始 secret 验签的结果不一致。修复签名 key 后此测试转绿。
 */
class WebhookSignatureKeyTest {

    @Test
    void hmacKeyShouldBeRawSecret_notSecretHash() throws Exception {
        Method hashMethod = WorkerService.class.getDeclaredMethod("hash", String.class);
        hashMethod.setAccessible(true);
        Method hmacMethod = TaskDispatchService.class.getDeclaredMethod("hmac", String.class, String.class);
        hmacMethod.setAccessible(true);

        // hash()/hmac() 均无状态、不依赖字段，构造参数传 null 即可。
        WorkerService workerService = new WorkerService(null, null, null, null);
        TaskDispatchService dispatcher = new TaskDispatchService(null, null, null, null, null, null);

        // 复刻 issueToken 的 secret 生成与入库哈希
        byte[] secretBytes = new byte[32];
        new SecureRandom().nextBytes(secretBytes);
        String webhookSigningSecret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String storedHash = (String) hashMethod.invoke(workerService, webhookSigningSecret);

        String timestamp = "1700000000000";
        String body = "{\"eventId\":1}";

        // 发送方当前实现：key = storedHash
        String senderSig = (String) hmacMethod.invoke(dispatcher, storedHash, timestamp + "." + body);
        // 接收方按惯例验签：key = webhookSigningSecret
        String receiverSig = (String) hmacMethod.invoke(dispatcher, webhookSigningSecret, timestamp + "." + body);

        assertEquals(receiverSig, senderSig,
            "HMAC key 必须用原始 signing secret；发送方却用了 secret 的哈希，导致接收方无法验签");
    }
}
