package com.smartlab.erp.meeting.service;

import com.smartlab.erp.meeting.config.TencentMeetingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@Slf4j
public class TencentMeetingSignatureService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final char[] HEX_CHAR = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private final TencentMeetingConfig config;

    public TencentMeetingSignatureService(TencentMeetingConfig config) {
        this.config = config;
    }

    /**
     * 生成签名所需的公共参数
     * @return 包含所有必要 Header 的 Map
     */
    public Map<String, String> generateCommonHeaders() {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = String.valueOf(Math.abs(UUID.randomUUID().hashCode()));

        Map<String, String> headers = new TreeMap<>();
        headers.put("AppId", config.getAppId());
        if (config.getSdkId() != null && !config.getSdkId().isEmpty()) {
            headers.put("SdkId", config.getSdkId());
        }
        headers.put("X-TC-Key", config.getSecretId());
        headers.put("X-TC-Timestamp", timestamp);
        headers.put("X-TC-Nonce", nonce);
        headers.put("Content-Type", "application/json");
        headers.put("X-TC-Registered", "1");

        return headers;
    }

    /**
     * 计算签名
     * @param httpMethod HTTP 方法 (GET/POST)
     * @param requestUri 请求 URI (如 /v1/meetings)
     * @param requestBody 请求体 (GET 请求传空串)
     * @param headers 参与签名的 Header (X-TC-Key, X-TC-Nonce, X-TC-Timestamp)
     * @return Base64 编码的签名
     */
    public String calculateSignature(String httpMethod, String requestUri, String requestBody,
                                     Map<String, String> headers) throws NoSuchAlgorithmException, InvalidKeyException {
        // 1. 构造 headerString (按参数名字典序排列)
        String headerString = "X-TC-Key=" + headers.get("X-TC-Key")
                + "&X-TC-Nonce=" + headers.get("X-TC-Nonce")
                + "&X-TC-Timestamp=" + headers.get("X-TC-Timestamp");

        // 2. 构造签名串
        String stringToSign = httpMethod + "\n"
                + headerString + "\n"
                + requestUri + "\n"
                + (requestBody != null ? requestBody : "");

        // DEBUG: 打印签名相关信息
        log.info("[TencentMeeting] === SIGN DEBUG ===");
        log.info("[TencentMeeting] HTTP Method: {}", httpMethod);
        log.info("[TencentMeeting] Request URI: {}", requestUri);
        log.info("[TencentMeeting] Header String: {}", headerString);
        log.info("[TencentMeeting] Request Body: {}", requestBody);
        log.info("[TencentMeeting] String to Sign:\n{}", stringToSign);
        log.info("[TencentMeeting] Secret Key (前10位): {}...",
                config.getSecretKey() != null ? config.getSecretKey().substring(0, Math.min(10, config.getSecretKey().length())) : "null");

        // 3. 计算 HmacSHA256
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                config.getSecretKey().getBytes(StandardCharsets.UTF_8),
                mac.getAlgorithm()
        );
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));

        // 4. 先转十六进制小写，再 Base64 编码（V1 API 签名规范）
        String hexHash = bytesToHex(hash);
        String signature = Base64.getEncoder().encodeToString(hexHash.getBytes(StandardCharsets.UTF_8));
        log.info("[TencentMeeting] Generated Signature: {}", signature);
        log.info("[TencentMeeting] === END SIGN DEBUG ===");

        return signature;
    }

    private String bytesToHex(byte[] bytes) {
        char[] buf = new char[bytes.length * 2];
        int index = 0;
        for (byte b : bytes) {
            buf[index++] = HEX_CHAR[b >>> 4 & 0xf];
            buf[index++] = HEX_CHAR[b & 0xf];
        }
        return new String(buf);
    }
}
