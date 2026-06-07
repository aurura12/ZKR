package com.smartlab.erp.config;

import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.security.JwtAuthenticationFilter;
import com.smartlab.erp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] FINANCE_API_PATTERNS = {
            "/api/finance/**",
            "/api/adjustment/**",
            "/api/batch/**",
            "/api/clearing/**",
            "/api/dividend/**",
            "/api/ai/**",
            "/api/rag/**"
    };

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserRepository userRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 🟢 修复 1：CORS 彻底放开，适配内网穿透域名
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // ⚠️ 关键修改：从 localhost 改为 *，允许 Cloudflare/公网域名访问
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. 优先放行 OPTIONS (跨域预检)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 2. 静态资源 (显式放行，防止意外)
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**").permitAll()

                        // 3. 🟢 关键改变：API 接口依然严格管控
                        //    (注意顺序：先放行登录/注册 API，再拦截其他 API)
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll() // 注册接口
                        .requestMatchers("/api/attendance/**").access(this::requireAttendanceDomain)
                        .requestMatchers("/api/messages/**").access(this::requireAttendanceDomain)
                        .requestMatchers(FINANCE_API_PATTERNS).access(this::requireFinanceDomain)
                        .requestMatchers("/api/**").access(this::requireErpDomain)

                        // 4. 🟢 核心大招：除了上面 /api/** 以外的所有路径，统统放行！
                        //    这样 /login, /projects, /dashboard 这些前端路由就能通过保安检查了
                        //    (它们通过后会报 404，然后被 SpaRedirectConfig 转发给 index.html)
                        .anyRequest().permitAll()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("{\"message\":\"当前账号无此权限\"}");
                        })
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write("{\"message\":\"当前账号未登录或登录已过期\"}");
                        })
                );

        return http.build();
    }

    private AuthorizationDecision requireFinanceDomain(Supplier<Authentication> authentication,
                                                        RequestAuthorizationContext context) {
        return requireAccountDomain(authentication, AccountDomain.FINANCE);
    }

    private AuthorizationDecision requireAttendanceDomain(Supplier<Authentication> authentication,
                                                       RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            return new AuthorizationDecision(false);
        }

        AccountDomain domain = userPrincipal.getAccountDomain();
        return new AuthorizationDecision(domain == AccountDomain.ERP || domain == AccountDomain.FINANCE);
    }

    private AuthorizationDecision requireErpDomain(Supplier<Authentication> authentication,
                                                    RequestAuthorizationContext context) {
        return requireAccountDomain(authentication, AccountDomain.ERP);
    }

    private AuthorizationDecision requireAccountDomain(Supplier<Authentication> authenticationSupplier,
                                                       AccountDomain requiredDomain) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            return new AuthorizationDecision(false);
        }

        return new AuthorizationDecision(requiredDomain.equals(userPrincipal.getAccountDomain()));
    }
}
