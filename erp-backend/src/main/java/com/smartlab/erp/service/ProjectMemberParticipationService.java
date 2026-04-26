package com.smartlab.erp.service;

import com.smartlab.erp.entity.ProjectMemberParticipationHistory;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.repository.ProjectMemberParticipationHistoryRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMemberParticipationService {

    private final ProjectMemberParticipationHistoryRepository historyRepository;
    private final SysProjectMemberRepository projectMemberRepository;
    private final SysProjectRepository projectRepository;

    @Transactional
    public void recordJoin(SysProjectMember member) {
        if (member == null || member.getProjectId() == null || member.getUser() == null || member.getUser().getUserId() == null) {
            return;
        }
        recordJoin(member.getProjectId(), member.getUser(), member.getJoinedAt());
    }

    @Transactional
    public void recordJoin(String projectId, User user, Instant joinedAt) {
        if (projectId == null || projectId.isBlank() || user == null || user.getUserId() == null || user.getUserId().isBlank()) {
            return;
        }
        SysProject project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }
        historyRepository.findTopByProject_ProjectIdAndUser_UserIdAndLeftAtIsNullOrderByJoinedAtDesc(projectId, user.getUserId())
                .ifPresentOrElse(existing -> {
                    if (existing.getJoinedAt() == null && joinedAt != null) {
                        existing.setJoinedAt(joinedAt);
                        historyRepository.save(existing);
                    }
                }, () -> historyRepository.save(ProjectMemberParticipationHistory.builder()
                        .project(project)
                        .user(user)
                        .joinedAt(joinedAt == null ? Instant.now() : joinedAt)
                        .build()));
    }

    @Transactional
    public void recordLeave(String projectId, String userId, Instant leftAt) {
        if (projectId == null || projectId.isBlank() || userId == null || userId.isBlank()) {
            return;
        }
        historyRepository.findTopByProject_ProjectIdAndUser_UserIdAndLeftAtIsNullOrderByJoinedAtDesc(projectId, userId)
                .ifPresent(history -> {
                    history.setLeftAt(leftAt == null ? Instant.now() : leftAt);
                    historyRepository.save(history);
                });
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberParticipationHistory> findByProjectId(String projectId) {
        return historyRepository.findByProject_ProjectId(projectId);
    }

    @Transactional
    public void ensureCurrentMemberHistories(String projectId) {
        projectMemberRepository.findByProjectIdWithUser(projectId).stream()
                .filter(member -> member.getUser() != null && Boolean.TRUE.equals(member.getUser().getActive()))
                .forEach(this::recordJoin);
    }
}
