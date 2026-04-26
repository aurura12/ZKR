package com.smartlab.erp.repository;

import com.smartlab.erp.entity.ProjectMemberParticipationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberParticipationHistoryRepository extends JpaRepository<ProjectMemberParticipationHistory, Long> {

    List<ProjectMemberParticipationHistory> findByProject_ProjectId(String projectId);

    List<ProjectMemberParticipationHistory> findByProject_ProjectIdIn(List<String> projectIds);

    Optional<ProjectMemberParticipationHistory> findTopByProject_ProjectIdAndUser_UserIdAndLeftAtIsNullOrderByJoinedAtDesc(String projectId, String userId);

    Optional<ProjectMemberParticipationHistory> findTopByProject_ProjectIdAndUser_UserIdOrderByJoinedAtDesc(String projectId, String userId);
}
