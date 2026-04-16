package com.smartlab.erp.service;

import com.smartlab.erp.dto.LeaderDashboardResponse;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.entity.UserRole;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderDashboardService {

    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final SysProjectRepository projectRepository;

    /**
     * 检查用户是否是某个角色的队长
     */
    public boolean isLeader(String userId, String role) {
        return userRoleRepository.existsByUserIdAndRoleAndIsLeaderTrue(userId, role.toUpperCase());
    }

    /**
     * 获取队长的仪表盘数据
     */
    @Transactional(readOnly = true)
    public LeaderDashboardResponse getLeaderDashboard(String leaderUserId, String role) {
        String normalizedRole = role.toUpperCase();

        // 验证是否是队长
        if (!isLeader(leaderUserId, normalizedRole)) {
            throw new RuntimeException("无权访问: 用户不是该角色的队长");
        }

        // 获取队长信息
        User leader = userRepository.findById(leaderUserId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 获取该角色下的所有成员
        List<UserRole> roleMembers = userRoleRepository.findByRoleWithUser(normalizedRole);

        // 构建成员项目参与信息
        List<LeaderDashboardResponse.MemberProjectInfo> memberInfos = roleMembers.stream()
                .map(ur -> buildMemberProjectInfo(ur.getUser()))
                .collect(Collectors.toList());

        return LeaderDashboardResponse.builder()
                .leaderRole(normalizedRole)
                .leaderName(leader.getName())
                .members(memberInfos)
                .build();
    }

    private LeaderDashboardResponse.MemberProjectInfo buildMemberProjectInfo(User user) {
        // 获取该成员参与的所有项目
        List<SysProjectMember> projectMembers = projectMemberRepository.findByProjectIdWithUser(user.getUserId())
                .stream()
                .filter(pm -> pm.getUser() != null && pm.getUser().getUserId().equals(user.getUserId()))
                .collect(Collectors.toList());

        // 重新查询以获取完整的项目信息
        List<String> projectIds = projectMembers.stream()
                .map(SysProjectMember::getProjectId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, SysProject> projectMap = projectRepository.findAllById(projectIds)
                .stream()
                .collect(Collectors.toMap(SysProject::getProjectId, p -> p));

        // 构建项目参与列表
        List<LeaderDashboardResponse.ProjectParticipation> participations = projectMembers.stream()
                .map(pm -> {
                    SysProject project = projectMap.get(pm.getProjectId());
                    if (project == null) return null;

                    // 获取项目经理名称
                    String managerName = Optional.ofNullable(project.getManager())
                            .map(User::getName)
                            .orElse("未知");
//                    String managerName = Optional.ofNullable(project.getManager().getUserId())
//                            .flatMap(managerId -> userRepository.findById(managerId).orElse(null))
//                            .map(User::getName)
//                            .orElse("未知");

                    return LeaderDashboardResponse.ProjectParticipation.builder()
                            .projectId(pm.getProjectId())
                            .projectName(project.getName())
                            .projectType(project.getProjectType()!=null ? project.getProjectType().name() : "PROJECT")
                            .flowType(project.getFlowType() != null ? project.getFlowType().name() : "PROJECT")
                            .memberRole(pm.getRole())
                            .weight(pm.getWeight() != null ? pm.getWeight() : 0)
                            .status(project.getCurrentStatus())
                            .managerName(managerName)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 计算总权重
        int totalWeight = participations.stream()
                .mapToInt(LeaderDashboardResponse.ProjectParticipation::getWeight)
                .sum();

        // 估算成本 (假设每人天成本为 1000 元,可根据实际情况调整)
        double estimatedCost = totalWeight * 1000.0;

        return LeaderDashboardResponse.MemberProjectInfo.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .name(user.getName())
                .avatar(user.getAvatar())
                .projects(participations)
                .totalWeight(totalWeight)
                .estimatedCost(estimatedCost)
                .build();
    }

    /**
     * 为用户分配角色
     */
    @Transactional
    public void assignRoleToUser(String userId, String role, Boolean isLeader) {
        String normalizedRole = role.toUpperCase();

        // 检查是否已存在
        Optional<UserRole> existing = userRoleRepository.findByUserIdAndRole(userId, normalizedRole);

        if (existing.isPresent()) {
            UserRole userRole = existing.get();
            userRole.setIsLeader(isLeader != null ? isLeader : false);
            userRoleRepository.save(userRole);
        } else {
            UserRole userRole = UserRole.builder()
                    .userId(userId)
                    .role(normalizedRole)
                    .isLeader(isLeader != null ? isLeader : false)
                    .build();
            userRoleRepository.save(userRole);
        }
    }

    /**
     * 移除用户的角色
     */
    @Transactional
    public void removeRoleFromUser(String userId, String role) {
        userRoleRepository.findByUserIdAndRole(userId, role.toUpperCase())
                .ifPresent(userRoleRepository::delete);
    }

    /**
     * 获取某角色的所有成员
     */
    @Transactional(readOnly = true)
    public List<User> getMembersByRole(String role) {
        return userRoleRepository.findByRoleWithUser(role.toUpperCase())
                .stream()
                .map(UserRole::getUser)
                .collect(Collectors.toList());
    }
}
