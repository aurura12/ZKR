package com.smartlab.erp.meeting.repository;

import com.smartlab.erp.meeting.entity.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, Long> {

    List<MeetingParticipant> findByMeetingRecordId(Long meetingRecordId);

    List<MeetingParticipant> findByUserId(String userId);

    List<MeetingParticipant> findByMeetingRecordIdAndUserId(Long meetingRecordId, String userId);

    boolean existsByMeetingRecordIdAndUserId(Long meetingRecordId, String userId);
}
