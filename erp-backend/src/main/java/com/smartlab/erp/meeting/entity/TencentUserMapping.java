package com.smartlab.erp.meeting.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tencent_user_mapping")
public class TencentUserMapping {

    @Id
    @Column(name = "erp_user_id", length = 64)
    private String erpUserId;

    @Column(name = "tencent_user_id", nullable = false, length = 128)
    private String tencentUserId;

    @Column(name = "tencent_username", length = 128)
    private String tencentUsername;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "remark", length = 200)
    private String remark;

    @Column(name = "can_create", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    @Builder.Default
    private Boolean canCreate = false;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
