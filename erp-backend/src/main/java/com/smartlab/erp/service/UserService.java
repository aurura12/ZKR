package com.smartlab.erp.service;

import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.entity.ProjectMemberParticipationHistory;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.repository.ProjectMemberParticipationHistoryRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.UserBadgeRepository;
import com.smartlab.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 用户业务逻辑层
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final SysProjectRepository projectRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final ProjectMemberParticipationHistoryRepository historyRepository;

    private static boolean isActive(User user) {
        return user != null && Boolean.TRUE.equals(user.getActive());
    }

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
                .filter(UserService::isActive)
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
                .filter(UserService::isActive)
                .peek(this::enrichUser)
                .toList();
    }

    public List<User> findAllUsersIncludingInactive() {
        return userRepository.findAll().stream()
                .peek(this::enrichUser)
                .toList();
    }

    @Transactional
    public void updateDailyWage(String userId, BigDecimal dailyWage) {
        if (dailyWage == null || dailyWage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("日工资必须大于0");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        user.setDailyWage(dailyWage);
        userRepository.save(user);
    }

    @Transactional
    public void deactivateUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new RuntimeException("该用户已离职");
        }
        user.setActive(false);
        userRepository.save(user);
    }

    @Transactional
    public void activateUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
        if (Boolean.TRUE.equals(user.getActive())) {
            throw new RuntimeException("该用户未离职");
        }
        user.setActive(true);
        userRepository.save(user);

        List<ProjectMemberParticipationHistory> histories = historyRepository.findByUser_UserId(userId);
        for (ProjectMemberParticipationHistory h : histories) {
            String projectId = h.getProject().getProjectId();
            if (!projectMemberRepository.existsByProjectIdAndUserUserId(projectId, userId)) {
                SysProject project = projectRepository.findById(projectId).orElse(null);
                if (project != null) {
                    SysProjectMember member = SysProjectMember.builder()
                            .projectId(projectId)
                            .user(user)
                            .role("MEMBER")
                            .joinedAt(h.getJoinedAt())
                            .build();
                    projectMemberRepository.save(member);
                }
            }
            if (h.getLeftAt() != null) {
                Optional<ProjectMemberParticipationHistory> latest = historyRepository
                        .findTopByProject_ProjectIdAndUser_UserIdOrderByJoinedAtDesc(projectId, userId);
                if (latest.isPresent() && latest.get().getLeftAt() == null) {
                    h.setLeftAt(null);
                    historyRepository.save(h);
                }
            }
        }
    }
}
