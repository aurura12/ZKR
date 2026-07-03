package com.smartlab.erp.ocr;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "erp.ocr")
public class OcrProperties {

    private String baseUrl = "http://erp-paddle-ocr:18952";

    private int connectTimeoutSeconds = 10;

    private int readTimeoutSeconds = 120;
}
