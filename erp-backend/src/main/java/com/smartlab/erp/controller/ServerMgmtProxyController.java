package com.smartlab.erp.controller;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/server-mgmt")
public class ServerMgmtProxyController {

    private static final Logger log = LoggerFactory.getLogger(ServerMgmtProxyController.class);

    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    @Value("${server.mgmt.api.base-url:http://server-mgmt-api:17000}")
    private String backendBaseUrl;

    public ServerMgmtProxyController(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.restTemplate = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
    }

    @RequestMapping("/**")
    public ResponseEntity<?> proxy(HttpServletRequest request) {
        requireServerOpsAdmin();

        String remainingPath = extractRemainingPath(request);
        String queryString = request.getQueryString();
        String targetUrl = backendBaseUrl + "/api" + remainingPath;
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        HttpMethod method = HttpMethod.valueOf(request.getMethod().toUpperCase());
        HttpHeaders headers = new HttpHeaders();

        String body = null;
        if (!"GET".equalsIgnoreCase(request.getMethod()) && !"HEAD".equalsIgnoreCase(request.getMethod())) {
            try {
                body = new String(request.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("Failed to read request body from {} {}", request.getMethod(), request.getRequestURI(), e);
            }
            if (body != null && !body.isEmpty()) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(targetUrl, method, entity, String.class);
            HttpHeaders safeHeaders = new HttpHeaders();
            response.getHeaders().forEach((key, values) -> {
                String lowerKey = key.toLowerCase();
                if (!"set-cookie".equals(lowerKey) && !"server".equals(lowerKey)
                        && !lowerKey.startsWith("x-internal-")) {
                    safeHeaders.put(key, values);
                }
            });
            return ResponseEntity.status(response.getStatusCode())
                    .headers(safeHeaders)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("Server mgmt proxy failed: {} {}", method, targetUrl, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "服务器管理服务暂时不可用，请稍后重试"));
        }
    }

    private void requireServerOpsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new PermissionDeniedException("无服务器管理权限");
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getServerOpsAdmin())) {
            throw new PermissionDeniedException("无服务器管理权限");
        }
    }

    private String extractRemainingPath(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathWithinApp = fullPath.substring(contextPath.length());
        return pathWithinApp.substring("/api/server-mgmt".length());
    }
}
