package com.smartlab.erp.meeting.scheduler;

import com.smartlab.erp.meeting.entity.MeetingParticipant;
import com.smartlab.erp.meeting.entity.MeetingRecord;
import com.smartlab.erp.meeting.repository.MeetingParticipantRepository;
import com.smartlab.erp.meeting.repository.MeetingRecordRepository;
import com.smartlab.erp.service.InternalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetingReminderScheduler {

    private final MeetingRecordRepository meetingRecordRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final InternalMessageService internalMessageService;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void sendMeetingReminders() {
        LocalDateTime threshold = LocalDateTime.now().plusMinutes(15);

        List<MeetingRecord> upcoming = meetingRecordRepository
                .findByStatusAndStartTimeBeforeAndLastRemindedAtIsNullOrderByStartTimeAsc("SCHEDULED", threshold);

        for (MeetingRecord meeting : upcoming) {
            try {
                List<MeetingParticipant> participants = meetingParticipantRepository
                        .findByMeetingRecordId(meeting.getId());

                if (participants.isEmpty()) continue;

                for (MeetingParticipant p : participants) {
                    internalMessageService.sendMessage(
                            p.getUserId(),
                            "MEETING_REMINDER",
                            "\u23F0 " + meeting.getTopic(),
                            "会议即将开始：" + meeting.getStartTime().format(TIME_FMT)
                                    + "\n入会链接：" + (meeting.getJoinUrl() != null ? meeting.getJoinUrl() : "请查看会议详情"),
                            meeting.getProjectId()
                    );
                }

                meeting.setLastRemindedAt(Instant.now());
                meetingRecordRepository.save(meeting);

                log.info("[会议提醒] 已发送 {} 个参会人提醒, meetingId={}, topic={}",
                        participants.size(), meeting.getMeetingId(), meeting.getTopic());

            } catch (Exception e) {
                log.error("[会议提醒] 发送失败 meetingId={}: {}", meeting.getMeetingId(), e.getMessage());
            }
        }
    }
}
