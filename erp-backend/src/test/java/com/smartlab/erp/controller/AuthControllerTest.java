package com.smartlab.erp.controller;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.smartlab.erp.config.JwtUtil;
import com.smartlab.erp.dto.LoginRequest;
import com.smartlab.erp.dto.RegisterRequest;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.finance.service.FinanceReferenceService;
import com.smartlab.erp.repository.EmailVerificationCodeRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.service.AuthService;
import com.smartlab.erp.service.MailService;
import com.smartlab.erp.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    void registerBindsErpDomain() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "erp-user",
                                  "password": "secret123",
                                  "name": "ERP User",
                                  "email": "erp@example.com",
                                  "role": "BUSINESS",
                                  "domain": "ERP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string("注册成功，请登录"));

        verify(authService).register(argThat((RegisterRequest request) ->
                request != null
                        && request.getDomain() == AccountDomain.ERP
                        && "erp-user".equals(request.getUsername())
        ));
    }

    @Test
    void loginBindsErpDomain() throws Exception {
        given(authService.login(org.mockito.ArgumentMatchers.any(LoginRequest.class)))
                .willReturn(Map.of("token", "erp-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "erp-user",
                                  "password": "secret123",
                                  "domain": "ERP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("erp-token"));

        verify(authService).login(argThat((LoginRequest request) ->
                request != null
                        && request.getDomain() == AccountDomain.ERP
                        && "erp-user".equals(request.getUsername())
        ));
    }

    @Test
    void registerRejectsMissingDomainFieldClearly() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "erp-user",
                                  "password": "secret123",
                                  "name": "ERP User",
                                  "email": "erp@example.com",
                                  "role": "BUSINESS"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号域不能为空"));
    }

    @Test
    void registerRejectsInvalidDomainFieldClearly() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "erp-user",
                                  "password": "secret123",
                                  "name": "ERP User",
                                  "email": "erp@example.com",
                                  "role": "BUSINESS",
                                  "domain": "INVALID"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号域取值无效"));
    }

    @Test
    void loginRejectsMissingDomainField() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "finance-user",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("账号域不能为空"));
    }

    @Test
    void handleUnreadableUsesCausePathForInvalidDomain() {
        InvalidFormatException cause = InvalidFormatException.from(null, "bad enum", "INVALID", AccountDomain.class);
        cause.prependPath(new RegisterRequest(), "domain");
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("bad payload", cause, null);

        ResponseEntity<Map<String, String>> response = new AuthController(authService).handleUnreadable(exception);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(response.getBody()).containsEntry("message", "账号域取值无效");
    }

    @Test
    void loginReturnsFinanceToErpMismatchMessage() throws Exception {
        doThrow(new BusinessException("该账号仅允许登录财务系统"))
                .when(authService)
                .login(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "finance-user",
                                  "password": "secret123",
                                  "domain": "ERP"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该账号仅允许登录财务系统"));
    }

    @Test
    void loginReturnsErpToFinanceMismatchMessage() throws Exception {
        doThrow(new BusinessException("该账号不属于财务系统"))
                .when(authService)
                .login(org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "erp-user",
                                  "password": "secret123",
                                  "domain": "FINANCE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该账号不属于财务系统"));
    }

    @Test
    void meReturnsAccountDomain() throws Exception {
        UserRepository localUserRepository = mock(UserRepository.class);
        JwtUtil localJwtUtil = mock(JwtUtil.class);
        PasswordEncoder localPasswordEncoder = mock(PasswordEncoder.class);
        AuthenticationManager localAuthenticationManager = mock(AuthenticationManager.class);
        MailService localMailService = mock(MailService.class);
        UserService localUserService = mock(UserService.class);
        EmailVerificationCodeRepository localEmailVerificationCodeRepository = mock(EmailVerificationCodeRepository.class);
        FinanceReferenceService localFinanceReferenceService = mock(FinanceReferenceService.class);
        AuthService realAuthService = new AuthService(
                localUserRepository,
                localPasswordEncoder,
                localJwtUtil,
                localAuthenticationManager,
                localMailService,
                localUserService,
                localEmailVerificationCodeRepository,
                localFinanceReferenceService
        );
        MockMvc standaloneMvc = MockMvcBuilders.standaloneSetup(new AuthController(realAuthService)).build();

        UserPrincipal principal = new UserPrincipal(
                "000001",
                "finance-user",
                "Finance User",
                "RESEARCH",
                "finance@example.com",
                AccountDomain.FINANCE,
                List.of(new SimpleGrantedAuthority("ROLE_RESEARCH"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        when(localUserRepository.findById("000001")).thenReturn(Optional.empty());

        try {
            standaloneMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountDomain").value("FINANCE"));
        } finally {
            clearInvocations(localUserRepository);
            SecurityContextHolder.clearContext();
        }
    }
}
