package com.smartlab.erp.meeting.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TencentMeetingConfig {

    @Value("${tencent.meeting.app-id:}")
    private String appId;

    @Value("${tencent.meeting.sdk-id:}")
    private String sdkId;

    @Value("${tencent.meeting.secret-id}")
    private String secretId;

    @Value("${tencent.meeting.secret-key}")
    private String secretKey;

    @Value("${tencent.meeting.webhook-secret:}")
    private String webhookSecret;

    @Value("${tencent.meeting.api-base-url:https://api.meeting.qq.com}")
    private String apiBaseUrl;

    public String getAppId() {
        return appId;
    }

    public String getSdkId() {
        return sdkId;
    }

    public String getSecretId() {
        return secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }
}
