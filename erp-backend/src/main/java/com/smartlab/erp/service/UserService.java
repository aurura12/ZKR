package com.smartlab.erp.service;

import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.repository.UserBadgeRepository;
import com.smartlab.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户业务逻辑层
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;

    /**
     * ✅ 查询所有用户
     * 用于前端"新建项目"时的团队成员选择
     */
    public List<User> findAllUsers(AccountDomain accountDomain) {
        AccountDomain effectiveDomain = accountDomain == null ? AccountDomain.ERP : accountDomain;
        return userRepository.findAll().stream()
                .filter(user -> {
                    AccountDomain userDomain = user.getAccountDomain() == null ? AccountDomain.ERP : user.getAccountDomain();
                    return userDomain == effectiveDomain;
                })
                .peek(this::enrichUser)
                .toList();
    }

    /**
     * 更新用户头像
     */
    @Transactional
    public void updateAvatar(String userId, String avatar) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setAvatar(avatar);
        userRepository.save(user);
    }

    public User enrichUser(User user) {
        user.setBadges(userBadgeRepository.findByUserIdOrderByCreatedAtDesc(user.getUserId()));
        return user;
    }

    public List<User> findAllUsers() {
        return userRepository.findAll().stream()
                .peek(this::enrichUser)
                .toList();
    }

    @Transactional
    public void updateDailyWage(String userId, BigDecimal dailyWage) {
        if (dailyWage == null || dailyWage.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("日工资不能为负数");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        user.setDailyWage(dailyWage);
        userRepository.save(user);
    }
}
