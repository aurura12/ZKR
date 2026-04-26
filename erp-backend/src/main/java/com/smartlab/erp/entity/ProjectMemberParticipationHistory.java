package com.smartlab.erp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "project_member_participation_history", indexes = {
        @Index(name = "idx_project_member_participation_project_user_joined", columnList = "project_id,user_id,joined_at"),
        @Index(name = "idx_project_member_participation_project_left", columnList = "project_id,left_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMemberParticipationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private SysProject project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.joinedAt == null) {
            this.joinedAt = Instant.now();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
