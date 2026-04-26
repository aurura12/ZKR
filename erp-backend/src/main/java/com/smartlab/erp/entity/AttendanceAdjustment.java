package com.smartlab.erp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "attendance_adjustment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "adjust_date", nullable = false)
    private LocalDate adjustDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "original_data", columnDefinition = "jsonb")
    private Map<String, Object> originalData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "adjusted_data", columnDefinition = "jsonb")
    private Map<String, Object> adjustedData;

    @Column(name = "reason", nullable = false, length = 256)
    private String reason;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
