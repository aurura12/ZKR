package com.smartlab.erp.controller;

import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.DingTalkIntegrationException;
import com.smartlab.erp.exception.PermissionDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<Map<String, String>> handlePermissionDenied(PermissionDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "当前账号无此权限"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(DingTalkIntegrationException.class)
    public ResponseEntity<Map<String, String>> handleDingTalkIntegration(DingTalkIntegrationException ex) {
        log.error("DingTalk integration failed at {}", ex.getStage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "系统内部错误，请稍后重试"));
    }
}
