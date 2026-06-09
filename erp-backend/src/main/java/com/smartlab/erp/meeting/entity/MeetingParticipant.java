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
@Table(name = "meeting_participant", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"meeting_record_id", "user_id"})
})
public class MeetingParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_record_id", nullable = false)
    private Long meetingRecordId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "attend_status", nullable = false, length = 20)
    @Builder.Default
    private String attendStatus = "PENDING";

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
