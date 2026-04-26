package com.smartlab.erp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_record",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "user_check_time", "check_type"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "user_name", length = 128)
    private String userName;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_type", length = 32)
    private String checkType;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "time_result", length = 32)
    private String timeResult;

    @Column(name = "location_result", length = 32)
    private String locationResult;

    @Column(name = "is_legal", length = 8)
    private String isLegal;

    @Column(name = "user_address", length = 256)
    private String userAddress;

    @Column(name = "user_longitude", length = 32)
    private String userLongitude;

    @Column(name = "user_latitude", length = 32)
    private String userLatitude;

    @Column(name = "base_address", length = 256)
    private String baseAddress;

    @Column(name = "user_check_time", nullable = false)
    private Instant userCheckTime;

    @Column(name = "plan_check_time")
    private Instant planCheckTime;

    @Column(name = "corp_id", length = 64)
    private String corpId;

    @Column(name = "group_id", length = 64)
    private String groupId;

    @Column(name = "class_id", length = 64)
    private String classId;

    @Column(name = "invalid_record_type", length = 64)
    private String invalidRecordType;

    @Column(name = "invalid_record_msg", length = 256)
    private String invalidRecordMsg;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "remark", length = 256)
    private String remark;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
