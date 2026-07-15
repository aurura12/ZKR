package com.smartlab.erp.service;

import com.smartlab.erp.dto.ChangePasswordRequest;
import com.smartlab.erp.dto.LoginRequest;
import com.smartlab.erp.dto.ProvisionUserRequest;
import com.smartlab.erp.dto.RegisterRequest;
import com.smartlab.erp.entity.EmailVerificationCode;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.exception.BusinessException;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.repository.EmailVerificationCodeRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.security.UserPrincipal;
import com.smartlab.erp.config.JwtUtil;
import com.smartlab.erp.finance.service.FinanceReferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 认证服务 - 核心业务逻辑
 * 统一使用 String 类型处理 UserId，不再进行 Long/String 转换补丁
 */
@Service
public class AuthService {

    private static final Set<String> REGISTER_ALLOWED_ROLES = Set.of("RESEARCH", "DATA", "DEV", "ALGORITHM", "BUSINESS", "PROMOTION", "CI");
    private static final Set<String> EXTRA_PROVISION_USERNAMES = Set.of("guojianwen", "jiaomiao", "admin", "leader");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final MailService mailService;
    private final UserService userService;
    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final FinanceReferenceService financeReferenceService;

    @Value("${auth.provisioning.admin-user-id:}")
    private String provisioningAdminUserId;

    @Value("${auth.provisioning.admin-username:Zhangqi}")
    private String provisioningAdminUsername;

    @Value("${auth.provisioning.admin-temp-password:}")
    private String provisioningAdminTempPassword;

    @Autowired
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager,
                       MailService mailService,
                       UserService userService,
                       EmailVerificationCodeRepository emailVerificationCodeRepository,
                       FinanceReferenceService financeReferenceService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.mailService = mailService;
        this.userService = userService;
        this.emailVerificationCodeRepository = emailVerificationCodeRepository;
        this.financeReferenceService = financeReferenceService;
    }

    /**
     * 生成下一个 6 位字符串格式的用户 ID
     * 逻辑：查询当前最大 ID -> 转数字 +1 -> 格式化回 6 位字符串
     */
    private synchronized String generateNextUserId() {
        // 依据 UserRepository 中的自定义查询获取当前最大 ID
        String maxId = userRepository.findMaxUserId().orElse("000000");
        try {
            int currentNum = Integer.parseInt(maxId);
            int nextNum = currentNum + 1;
            return String.format("%06d", nextNum);
        } catch (NumberFormatException e) {
            // 容错处理：若数据库存在非法 ID，则从 000001 开始
            return "000001";
        }
    }

    /**
     * 用户注册逻辑
     * 接收封装好的 RegisterRequest DTO，并进行参数解构
     */
    @Transactional
    public void register(RegisterRequest request) {
        throw new PermissionDeniedException("公开注册已关闭，请联系管理员创建账号");
    }

    @Transactional
    public String provisionUser(ProvisionUserRequest request) {
        User operator = getCurrentUser();
        if (!canProvisionAccounts(operator)) {
            throw new PermissionDeniedException("仅指定管理员可创建账号");
        }

        AccountDomain domain = resolveProvisionDomain(request.getDomain());
        validateUserUniqueness(request.getUsername());

        BigDecimal wage = request.getDailyWage();
        if (wage == null || wage.compareTo(BigDecimal.ZERO) < 0) {
            wage = new BigDecimal("300.00");
        }

        String normalizedUsername = request.getUsername().trim();
        String initialPassword = normalizedUsername + "123";
        User user = User.builder()
                .userId(generateNextUserId())
                .username(normalizedUsername)
                .password(passwordEncoder.encode(initialPassword))
                .name(request.getName().trim())
                .email(null)
                .role(normalizeRegisterRole(request.getRole()))
                .accountDomain(domain)
                .active(true)
                .dailyWage(wage)
                .idNumber(request.getIdNumber())
                .phone(request.getPhone())
                .bankName(request.getBankName())
                .bankAccount(request.getBankAccount())
                .ethnicity(request.getEthnicity())
                .position(request.getPosition())
                .partTime(request.getPartTime() != null ? request.getPartTime() : false)
                .paymentEntity(request.getPaymentEntity() != null ? request.getPaymentEntity() : "国科九天")
                .schoolDepartment(request.getSchoolDepartment())
                .address(request.getAddress())
                .build();

        userRepository.save(user);
        if (domain == AccountDomain.ERP) {
            financeReferenceService.getOrCreateWallet(user.getUserId());
        }
        return user.getUserId();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User currentUser = getCurrentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), currentUser.getPassword())) {
            throw new BusinessException("当前密码不正确");
        }

        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }

        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);
    }

    private String normalizeRegisterRole(String inputRole) {
        if (inputRole == null || inputRole.isBlank()) {
            throw new RuntimeException("注册失败：角色不能为空，仅支持 research/business/promotion/algorithm/data/dev/ci");
        }

        String normalized = inputRole.trim().toUpperCase(Locale.ROOT);
        if ("BUSNESS".equals(normalized)) {
            normalized = "BUSINESS";
        }
        if ("ALGO".equals(normalized)) {
            normalized = "ALGORITHM";
        }
        if ("REASE".equals(normalized)) {
            normalized = "RESEARCH";
        }
        if ("NORMAL".equals(normalized)) {
            normalized = "RESEARCH";
        }
        if ("COLLECTIVE_INTELLIGENCE".equals(normalized)) {
            normalized = "CI";
        }

        if (!REGISTER_ALLOWED_ROLES.contains(normalized)) {
            throw new RuntimeException("注册失败：角色非法，仅支持 research/business/promotion/algorithm/data/dev/ci");
        }

        return normalized;
    }

    /**
     * 用户登录逻辑
     * 认证通过后生成包含 String UserId 的 JWT
     */
    public Map<String, String> login(LoginRequest request) {
        AccountDomain requestedDomain = request.getDomain();
        if (requestedDomain == null) {
            throw new BusinessException("登录失败：账号域不能为空，仅支持 ERP/FINANCE");
        }

        // 1. 调用 Security 基础组件进行账号密码验证
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (AuthenticationException ex) {
            if (!isProvisionAdminEmergencyLogin(request)) {
                throw ex;
            }
        }

        // 2. 验证通过后检索用户信息
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("登录异常：用户信息同步失败"));

        AccountDomain storedDomain = user.getAccountDomain() == null ? AccountDomain.ERP : user.getAccountDomain();
        if (requestedDomain != storedDomain) {
            throw new BusinessException(requestedDomain == AccountDomain.FINANCE
                    ? "该账号不属于财务系统"
                    : "该账号仅允许登录财务系统");
        }

        // 3. 调用 JwtUtil 生成 Token
        // 传入 user.getUserId() (String 类型)，确保 Token 内部 Claims 类型一致
        String token = jwtUtil.generateToken(
                user.getUserId(),
                user.getUsername(),
                user.getName(),
                user.getRole(),
                user.getEmail(),
                storedDomain
        );

        // 4. 组装返回结果
        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        return response;
    }

    private AccountDomain resolveRegisterDomain(RegisterRequest request) {
        AccountDomain requestedDomain = request.getDomain();
        if (requestedDomain == null) {
            throw new RuntimeException("注册失败：账号域不能为空，仅支持 ERP/FINANCE");
        }

        if (requestedDomain != AccountDomain.ERP && requestedDomain != AccountDomain.FINANCE) {
            throw new RuntimeException("注册失败：账号域非法，仅支持 ERP/FINANCE");
        }

        return requestedDomain;
    }

    /**
     * 获取当前上下文中的用户信息
     * 核心逻辑：从 SecurityContextHolder 中提取 UserPrincipal
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new PermissionDeniedException("权限异常：当前未登录");
        }

        Object principal = authentication.getPrincipal();
        String userId;

        // 匹配之前修改过的 UserPrincipal 结构
        if (principal instanceof UserPrincipal) {
            UserPrincipal userPrincipal = (UserPrincipal) principal;
            userId = userPrincipal.getId(); // 此处获取的是 String ID

            User principalUser = buildCurrentUser(userPrincipal);
            return userRepository.findById(userId)
                    .map(user -> {
                        if (user.getAccountDomain() == null) {
                            user.setAccountDomain(principalUser.getAccountDomain());
                        }
                    return userService == null ? user : userService.enrichUser(user);
                })
                    .orElse(principalUser);
        } else {
            throw new RuntimeException("身份识别异常：无法解析当前用户标识");
        }
    }

    public boolean canProvisionAccounts(User user) {
        if (user == null) {
            return false;
        }
        if (provisioningAdminUserId != null && !provisioningAdminUserId.isBlank()) {
            if (provisioningAdminUserId.equals(user.getUserId())) {
                return true;
            }
        }
        if (provisioningAdminUsername != null
                && !provisioningAdminUsername.isBlank()
                && provisioningAdminUsername.equalsIgnoreCase(user.getUsername())) {
            return true;
        }
        return user.getUsername() != null
                && EXTRA_PROVISION_USERNAMES.contains(user.getUsername().trim().toLowerCase(Locale.ROOT));
    }

    private boolean isProvisionAdminEmergencyLogin(LoginRequest request) {
        return provisioningAdminTempPassword != null
                && !provisioningAdminTempPassword.isBlank()
                && provisioningAdminUsername != null
                && provisioningAdminUsername.equalsIgnoreCase(request.getUsername())
                && provisioningAdminTempPassword.equals(request.getPassword());
    }

    private User buildCurrentUser(UserPrincipal principal) {
        return User.builder()
                .userId(principal.getId())
                .username(principal.getUsername())
                .name(principal.getName())
                .role(principal.getRole())
                .email(principal.getEmail())
                .accountDomain(principal.getAccountDomain() == null ? AccountDomain.ERP : principal.getAccountDomain())
                .active(principal.isEnabled())
                .build();
    }

    private void validateUserUniqueness(String username) {
        if (userRepository.existsByUsername(username.trim())) {
            throw new BusinessException("用户名已存在");
        }
    }

    private AccountDomain resolveProvisionDomain(AccountDomain requestedDomain) {
        if (requestedDomain == null) {
            throw new BusinessException("账号域不能为空，仅支持 ERP/FINANCE");
        }
        if (requestedDomain != AccountDomain.ERP && requestedDomain != AccountDomain.FINANCE) {
            throw new BusinessException("账号域非法，仅支持 ERP/FINANCE");
        }
        return requestedDomain;
    }

    /**
     * Generate and send verification code to email
     * Cooldown: 1 minute between requests
     */
    @Transactional
    public void sendVerificationCode(String email) {
        // Validate email format
        if (email == null || email.isBlank()) {
            throw new BusinessException("邮箱不能为空");
        }

        // Check if user exists with this email
        User user = userRepository.findByEmailAndAccountDomain(email, AccountDomain.ERP)
                .orElseThrow(() -> new BusinessException("该邮箱未注册"));

        // Check cooldown: 1 minute between requests
        List<EmailVerificationCode> recentCodes = emailVerificationCodeRepository.findByEmailAndExpiresAtAfter(
                email, Instant.now().minusSeconds(60)
        );

        if (!recentCodes.isEmpty()) {
            throw new BusinessException("请求过于频繁，请 1 分钟后再试");
        }

        // Clean up old unverified codes for this email
        emailVerificationCodeRepository.deleteUnverifiedByEmail(email);

        // Generate 6-digit code
        String verificationCode = generateVerificationCode();

        // Store code with 5 minute expiration
        EmailVerificationCode codeEntity = EmailVerificationCode.builder()
                .email(email)
                .code(verificationCode)
                .expiresAt(Instant.now().plusSeconds(300)) // 5 minutes
                .verified(false)
                .build();

        emailVerificationCodeRepository.save(codeEntity);

        // Send email
        mailService.sendVerificationCode(email, verificationCode);
    }

    /**
     * Verify the verification code
     */
    @Transactional
    public void verifyCode(String email, String code) {
        EmailVerificationCode verificationCode = emailVerificationCodeRepository
                .findByEmailAndCodeAndExpiresAtAfter(email, code, Instant.now())
                .orElseThrow(() -> new BusinessException("验证码无效或已过期"));

        verificationCode.setVerified(true);
        emailVerificationCodeRepository.save(verificationCode);
    }

    /**
     * Reset password using verified code
     */
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        // Verify code first
        verifyCode(email, code);

        // Find user
        User user = userRepository.findByEmailAndAccountDomain(email, AccountDomain.ERP)
                .orElseThrow(() -> new BusinessException("该邮箱未注册"));

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Clean up used verification code
        emailVerificationCodeRepository.deleteUnverifiedByEmail(email);
    }

    private String generateVerificationCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(SECURE_RANDOM.nextInt(10));
        }
        return code.toString();
    }
}
