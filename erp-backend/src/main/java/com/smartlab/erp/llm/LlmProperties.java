package com.smartlab.erp.llm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String baseUrl = "https://opencode.ai/zen/go/v1";
    private String apiKey = "";
    private String model = "deepseek-v4-pro";
    private int timeoutSeconds = 60;
}
