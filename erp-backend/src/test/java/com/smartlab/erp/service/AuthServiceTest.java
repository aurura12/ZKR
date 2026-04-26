package com.smartlab.erp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlab.erp.config.AccountDomainDataInitializer;
import com.smartlab.erp.dto.LoginRequest;
import com.smartlab.erp.dto.RegisterRequest;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.smartlab.erp.config.JwtUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Validator validator;

    AuthServiceTest() {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        this.validator = validatorFactory.getValidator();
    }

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<List<User>> usersCaptor;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Test
    void bindsFinanceDomainOnAuthDtosAndUserIdentity() throws Exception {
        RegisterRequest registerRequest = objectMapper.readValue("""
                {
                  \"username\": \"finance-user\",
                  \"password\": \"secret123\",
                  \"name\": \"Finance User\",
                  \"email\": \"finance@example.com\",
                  \"role\": \"RESEARCH\",
                  \"domain\": \"FINANCE\"
                }
                """, RegisterRequest.class);
        LoginRequest loginRequest = objectMapper.readValue("""
                {
                  \"username\": \"finance-user\",
                  \"password\": \"secret123\",
                  \"domain\": \"FINANCE\"
                }
                """, LoginRequest.class);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);

        User user = User.builder()
                .userId("000003")
                .username(registerRequest.getUsername())
                .password("encoded-secret")
                .name(registerRequest.getName())
                .email(registerRequest.getEmail())
                .role(registerRequest.getRole())
                .accountDomain(registerRequest.getDomain())
                .active(true)
                .build();

        assertThat(violations).isEmpty();
        assertThat(registerRequest.getDomain()).isEqualTo(AccountDomain.FINANCE);
        assertThat(loginRequest.getDomain()).isEqualTo(AccountDomain.FINANCE);
        assertThat(user.getAccountDomain()).isEqualTo(AccountDomain.FINANCE);
    }

    @Test
    void rejectsMissingRegisterDomain() {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("finance-user");
        registerRequest.setPassword("secret123");
        registerRequest.setName("Finance User");
        registerRequest.setEmail("finance@example.com");
        registerRequest.setRole("RESEARCH");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(registerRequest);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("domain");
    }

    @Test
    void backfillsMissingAccountDomainToErpByDefault() {
        User legacyUser = User.builder()
                .userId("000001")
                .username("legacy-erp-user")
                .password("secret")
                .accountDomain(null)
                .active(true)
                .build();
        when(userRepository.findAll()).thenReturn(List.of(legacyUser));

        AccountDomainDataInitializer initializer = new AccountDomainDataInitializer(userRepository, "");

        initializer.backfillMissingAccountDomains();

        verify(userRepository).saveAll(usersCaptor.capture());
        assertThat(usersCaptor.getValue()).singleElement().satisfies(user -> {
            assertThat(user.getUsername()).isEqualTo("legacy-erp-user");
            assertThat(user.getAccountDomain()).isEqualTo(AccountDomain.ERP);
        });
    }

    @Test
    void backfillsAllowlistedFinanceUsersToFinance() {
        User financePilot = User.builder()
                .userId("000002")
                .username("finance-pilot")
                .password("secret")
                .accountDomain(null)
                .active(true)
                .build();
        when(userRepository.findAll()).thenReturn(List.of(financePilot));

        AccountDomainDataInitializer initializer = new AccountDomainDataInitializer(userRepository, "finance-pilot,another-user");

        initializer.backfillMissingAccountDomains();

        verify(userRepository).saveAll(usersCaptor.capture());
        assertThat(usersCaptor.getValue()).singleElement().satisfies(user -> {
            assertThat(user.getUsername()).isEqualTo("finance-pilot");
            assertThat(user.getAccountDomain()).isEqualTo(AccountDomain.FINANCE);
        });
    }

    @Test
    void registerPersistsFinanceDomain() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("finance-user");
        request.setPassword("secret123");
        request.setName("Finance User");
        request.setEmail("finance@example.com");
        request.setRole("BUSINESS");
        request.setDomain(AccountDomain.FINANCE);

        when(userRepository.existsByUsername("finance-user")).thenReturn(false);
        when(userRepository.findMaxUserId()).thenReturn(java.util.Optional.of("000009"));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");

        authService.register(request);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAccountDomain()).isEqualTo(AccountDomain.FINANCE);
    }

    @Test
    void registerPersistsErpDomain() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("erp-user");
        request.setPassword("secret123");
        request.setName("Erp User");
        request.setEmail("erp@example.com");
        request.setRole("RESEARCH");
        request.setDomain(AccountDomain.ERP);

        when(userRepository.existsByUsername("erp-user")).thenReturn(false);
        when(userRepository.findMaxUserId()).thenReturn(java.util.Optional.of("000019"));
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");

        authService.register(request);

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAccountDomain()).isEqualTo(AccountDomain.ERP);
    }

    @Test
    void registerRejectsMissingDomainClearly() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("missing-domain-user");
        request.setPassword("secret123");
        request.setName("Missing Domain User");
        request.setEmail("missing-domain@example.com");
        request.setRole("RESEARCH");

        when(userRepository.existsByUsername("missing-domain-user")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("注册失败：账号域不能为空，仅支持 ERP/FINANCE");
    }

    @Test
    void loginAllowsFinanceAccountWhenRequestedDomainIsFinance() {
        LoginRequest request = LoginRequest.builder()
                .username("finance-user")
                .password("secret123")
                .domain(AccountDomain.FINANCE)
                .build();
        User user = User.builder()
                .userId("000010")
                .username("finance-user")
                .password("encoded-secret")
                .name("Finance User")
                .email("finance@example.com")
                .role("BUSINESS")
                .accountDomain(AccountDomain.FINANCE)
                .active(true)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(org.mockito.Mockito.mock(Authentication.class));
        when(userRepository.findByUsername("finance-user")).thenReturn(java.util.Optional.of(user));
        when(jwtUtil.generateToken(eq("000010"), eq("finance-user"), eq("Finance User"), eq("BUSINESS"), eq("finance@example.com"), eq(AccountDomain.FINANCE)))
                .thenReturn("finance-token");

        Map<String, String> response = authService.login(request);

        assertThat(response).containsEntry("token", "finance-token");
    }

    @Test
    void loginRejectsMissingDomainClearly() {
        LoginRequest request = LoginRequest.builder()
                .username("finance-user")
                .password("secret123")
                .domain(null)
                .build();

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录失败：账号域不能为空，仅支持 ERP/FINANCE");
    }

    @Test
    void loginRejectsFinanceAccountWhenRequestedDomainIsErp() {
        LoginRequest request = LoginRequest.builder()
                .username("finance-user")
                .password("secret123")
                .domain(AccountDomain.ERP)
                .build();
        User user = User.builder()
                .userId("000010")
                .username("finance-user")
                .accountDomain(AccountDomain.FINANCE)
                .active(true)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(org.mockito.Mockito.mock(Authentication.class));
        when(userRepository.findByUsername("finance-user")).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该账号仅允许登录财务系统");
    }

    @Test
    void loginRejectsErpAccountWhenRequestedDomainIsFinance() {
        LoginRequest request = LoginRequest.builder()
                .username("erp-user")
                .password("secret123")
                .domain(AccountDomain.FINANCE)
                .build();
        User user = User.builder()
                .userId("000011")
                .username("erp-user")
                .accountDomain(AccountDomain.ERP)
                .active(true)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(org.mockito.Mockito.mock(Authentication.class));
        when(userRepository.findByUsername("erp-user")).thenReturn(java.util.Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该账号不属于财务系统");
    }
}
