package com.smartlab.erp.security;

import com.smartlab.erp.config.JwtUtil;
import com.smartlab.erp.enums.AccountDomain;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * JWT认证过滤器 - 强力调试版
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return "/api/auth/login".equals(requestUri)
                || "/api/auth/register".equals(requestUri)
                || "/api/auth/logout".equals(requestUri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // 🔍 DEBUG 1: 打印请求路径和Header
        System.out.println(">>> 正在访问: " + request.getRequestURI());

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            try {
                // 1. 解析 Token
                Map<String, Object> userInfo = jwtUtil.getUserInfoFromToken(token);

                // 🔍 DEBUG 2: 打印解析出来的所有数据，检查键名对不对
                System.out.println(">>> Token 解析成功，包含数据: " + userInfo);

                // 2. 获取 UserId (最容易出错的地方)
                Object userIdObj = userInfo.get("userId");
                System.out.println(">>> 尝试获取 userId: " + userIdObj + " (类型: " + (userIdObj == null ? "null" : userIdObj.getClass().getName()) + ")");

                if (userIdObj == null) {
                    // ⚠️ 如果这里报错，说明 JwtUtil 生成 Token 时用的键名可能不是 "userId" (可能是 "id" 或 "sub")
                    throw new RuntimeException("Token 中找不到 'userId' 字段！请检查 JwtUtil 生成逻辑。");
                }

                // 强转逻辑
                String userId = String.valueOf(userIdObj);

                String username = (String) userInfo.get("username");
                String role = (String) userInfo.get("role");
                String accountDomainValue = (String) userInfo.get("accountDomain");
                AccountDomain accountDomain = accountDomainValue == null
                        ? AccountDomain.ERP
                        : AccountDomain.valueOf(accountDomainValue);

                // 3. 组装权限
                Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

                // 🔍 DEBUG 3: 准备创建 UserPrincipal
                System.out.println(">>> 准备构建身份: ID=" + userId + ", Role=" + role);

                // 4. 构建 UserPrincipal
                UserPrincipal principal = new UserPrincipal(
                        userId,
                        username,
                        (String) userInfo.get("name"),
                        role,
                        (String) userInfo.get("email"),
                        accountDomain,
                        authorities
                );

                // 5. 构建 Authentication 对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);

                // 6. 放入上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 7. 设置 request attribute，供 Controller 使用
                request.setAttribute("userId", userId);

                System.out.println(">>> ✅ 认证成功！SecurityContext 已设置。");

            } catch (Exception e) {
                // 🛑 核心报错捕捉
                System.err.println(">>> ❌ JWT 认证过程中发生严重崩溃！原因如下：");
                e.printStackTrace(); // 把堆栈打印出来！
            }
        } else {
            System.out.println(">>> Header 中没有 Token，视为匿名访问");
        }

        filterChain.doFilter(request, response);
    }
}
