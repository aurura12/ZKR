package com.smartlab.erp.controller;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/server-mgmt")
@RequiredArgsConstructor
public class ServerMgmtProxyController {

    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${server.mgmt.api.base-url:http://server-mgmt-api:17000}")
    private String backendBaseUrl;

    @RequestMapping("/**")
    public ResponseEntity<?> proxy(HttpServletRequest request) {
        User currentUser = requireServerOpsAdmin();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "无服务器管理权限"));
        }

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
            } catch (Exception ignored) {
            }
            if (body != null && !body.isEmpty()) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
        }

        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(targetUrl, method, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "服务器管理服务不可用: " + e.getMessage()));
        }
    }

    private User requireServerOpsAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getServerOpsAdmin())) {
            return null;
        }
        return user;
    }

    private String extractRemainingPath(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        String pathWithinApp = fullPath.substring(contextPath.length());
        return pathWithinApp.substring("/api/server-mgmt".length());
    }
}
