package com.smartlab.erp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowMemberRoleDTO {
    private String workflowType;
    private String workflowId;
    private String userId;
    private String role;
    private String name;
    private String username;
    private String avatar;
    private Boolean hiddenAvatar;
}
