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

import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
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
        if (dailyWage == null || dailyWage.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("日工资不能为负数");
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

    public byte[] exportRosterXlsx() {
        List<User> allUsers = userRepository.findAllByOrderByUserIdAsc();
        try (var workbook = new XSSFWorkbook();
             var bos = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("日薪制");
            var headerStyle = workbook.createCellStyle();
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {"序号","姓名","部门","岗位","民族","入职日期","离职时间","联系电话","是否在职","是否兼职","月工资","身份证号码","支付主体","开户行","银行卡号"};
            var headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                var cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (User u : allUsers) {
                var row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(rowIdx - 1);
                String displayName = u.getName();
                if (displayName != null && (displayName.contains("实习") || (u.getPosition() != null && u.getPosition().contains("实习")))) {
                    displayName = displayName + "（实习生）";
                }
                row.createCell(1).setCellValue(displayName != null ? displayName : "");
                row.createCell(2).setCellValue(u.getDepartment() != null ? u.getDepartment() : "");
                row.createCell(3).setCellValue(u.getPosition() != null ? u.getPosition() : "");
                row.createCell(4).setCellValue(u.getEthnicity() != null ? u.getEthnicity() : "");
                row.createCell(5).setCellValue(u.getEntryDate() != null ? u.getEntryDate().toString() : "");
                row.createCell(6).setCellValue(u.getDepartureDate() != null ? u.getDepartureDate().toString() : "");
                row.createCell(7).setCellValue(u.getPhone() != null ? u.getPhone() : "");
                row.createCell(8).setCellValue(Boolean.TRUE.equals(u.getActive()) ? "是" : "否");
                row.createCell(9).setCellValue(Boolean.TRUE.equals(u.getPartTime()) ? "是" : "否");
                row.createCell(10).setCellValue((u.getDailyWage() != null ? u.getDailyWage().toString() : "300.00") + "/天");
                row.createCell(11).setCellValue(u.getIdNumber() != null ? u.getIdNumber() : "");
                row.createCell(12).setCellValue(u.getPaymentEntity() != null ? u.getPaymentEntity() : "国科九天");
                row.createCell(13).setCellValue(u.getBankName() != null ? u.getBankName() : "");
                row.createCell(14).setCellValue(u.getBankAccount() != null ? u.getBankAccount() : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出花名册失败", e);
        }
    }
}
