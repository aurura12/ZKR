package com.smartlab.erp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "dingtalk_user_directory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DingTalkUserDirectory {

    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "name", length = 128)
    private String name;

    @Column(name = "mobile", length = 32)
    private String mobile;

    @Column(name = "dept_ids_json", columnDefinition = "text")
    private String deptIdsJson;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "admin")
    private Boolean admin;

    @Column(name = "avatar", columnDefinition = "text")
    private String avatar;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
