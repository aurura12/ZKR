package com.smartlab.erp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderDashboardResponse {
    private String leaderRole;
    private String leaderName;
    private List<MemberProjectInfo> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberProjectInfo {
        private String userId;
        private String username;
        private String name;
        private String avatar;
        private List<ProjectParticipation> projects;
        private Integer totalWeight;
        private Double estimatedCost;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectParticipation {
        private String projectId;
        private String projectName;
        private String projectType;
        private String flowType;
        private String memberRole;
        private Integer weight;
        private String status;
        private String managerName;
    }
}
