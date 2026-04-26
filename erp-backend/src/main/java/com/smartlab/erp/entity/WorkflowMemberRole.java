package com.smartlab.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "workflow_member_role", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"workflow_type", "workflow_id", "user_id", "role"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowMemberRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_type", nullable = false, length = 32)
    private String workflowType;

    @Column(name = "workflow_id", nullable = false, length = 64)
    private String workflowId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String role;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
