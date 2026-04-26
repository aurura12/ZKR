package com.smartlab.erp.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "batch_job_control")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchJobControl {

    @Id
    @Column(name = "job_key", length = 64, nullable = false)
    private String jobKey;

    @Column(name = "display_name", length = 120, nullable = false)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "schedule_mode", length = 20, nullable = false)
    private String scheduleMode;

    @Column(name = "frequency_minutes")
    private Integer frequencyMinutes;

    @Column(name = "run_at_hour")
    private Integer runAtHour;

    @Column(name = "run_at_minute")
    private Integer runAtMinute;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "last_triggered_key", length = 64)
    private String lastTriggeredKey;

    @Column(name = "last_status", length = 20)
    private String lastStatus;

    @Column(name = "last_message")
    private String lastMessage;

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
