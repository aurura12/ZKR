package com.smartlab.erp.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "batch_project_cost_control")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchProjectCostControl {

    @Id
    @Column(name = "project_id", length = 64, nullable = false)
    private String projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    private com.smartlab.erp.entity.SysProject project;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "note")
    private String note;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
