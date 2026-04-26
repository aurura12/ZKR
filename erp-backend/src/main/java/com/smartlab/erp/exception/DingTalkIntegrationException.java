package com.smartlab.erp.exception;

import lombok.Getter;

@Getter
public class DingTalkIntegrationException extends RuntimeException {

    private final String stage;
    private final Integer dingTalkErrCode;
    private final String responseBody;

    public DingTalkIntegrationException(String stage, String message) {
        this(stage, message, null, null, null);
    }

    public DingTalkIntegrationException(String stage, String message, Integer dingTalkErrCode, String responseBody) {
        this(stage, message, dingTalkErrCode, responseBody, null);
    }

    public DingTalkIntegrationException(String stage, String message, Integer dingTalkErrCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.dingTalkErrCode = dingTalkErrCode;
        this.responseBody = responseBody;
    }
}
