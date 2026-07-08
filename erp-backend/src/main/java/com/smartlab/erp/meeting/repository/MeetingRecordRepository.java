package com.smartlab.erp.meeting.repository;

import com.smartlab.erp.meeting.entity.MeetingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingRecordRepository extends JpaRepository<MeetingRecord, Long> {

    Optional<MeetingRecord> findByMeetingId(String meetingId);

    List<MeetingRecord> findByCreatorIdOrderByStartTimeDesc(String creatorId);

    List<MeetingRecord> findByProjectIdOrderByStartTimeDesc(String projectId);

    List<MeetingRecord> findByStatusOrderByStartTimeDesc(String status);

    List<MeetingRecord> findByStartTimeBetweenOrderByStartTimeDesc(LocalDateTime start, LocalDateTime end);

    List<MeetingRecord> findByStartTimeBetweenAndStatusOrderByStartTimeDesc(
            LocalDateTime start, LocalDateTime end, String status);

    List<MeetingRecord> findByStatusAndStartTimeBeforeAndLastRemindedAtIsNullOrderByStartTimeAsc(
            String status, LocalDateTime before);
}
