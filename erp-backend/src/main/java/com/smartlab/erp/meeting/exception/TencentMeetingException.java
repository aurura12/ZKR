package com.smartlab.erp.meeting.exception;

import lombok.Getter;

@Getter
public class TencentMeetingException extends RuntimeException {

    private final String stage;
    private final String message;
    private final Integer errorCode;
    private final String rawResponse;

    public TencentMeetingException(String stage, String message, Integer errorCode, String rawResponse) {
        super(message);
        this.stage = stage;
        this.message = message;
        this.errorCode = errorCode;
        this.rawResponse = rawResponse;
    }

    public TencentMeetingException(String stage, String message, Integer errorCode, String rawResponse, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.message = message;
        this.errorCode = errorCode;
        this.rawResponse = rawResponse;
    }
}
