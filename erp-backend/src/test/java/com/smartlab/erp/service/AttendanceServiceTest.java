package com.smartlab.erp.service;

import com.smartlab.erp.entity.AttendanceRecord;
import com.smartlab.erp.entity.DingTalkUserDirectory;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.repository.AttendanceAdjustmentRepository;
import com.smartlab.erp.repository.AttendanceRecordRepository;
import com.smartlab.erp.repository.DingTalkUserDirectoryRepository;
import com.smartlab.erp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRecordRepository recordRepository;

    @Mock
    private AttendanceAdjustmentRepository adjustmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DingTalkUserDirectoryRepository dingTalkUserDirectoryRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    void resolvesSystemUserIdToDingTalkAttendanceByName() {
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 30);

        User systemUser = User.builder()
                .userId("000009")
                .name("张琦")
                .build();

        DingTalkUserDirectory directory = DingTalkUserDirectory.builder()
                .userId("01633264162836330711")
                .name("张琦")
                .build();

        AttendanceRecord record = AttendanceRecord.builder()
                .userId("01633264162836330711")
                .userName("张琦")
                .workDate(LocalDate.of(2026, 4, 16))
                .checkType("OnDuty")
                .userCheckTime(Instant.parse("2026-04-16T01:00:00Z"))
                .build();

        when(userRepository.findById("000009")).thenReturn(Optional.of(systemUser));
        when(dingTalkUserDirectoryRepository.findAll()).thenReturn(List.of(directory));
        when(recordRepository.findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(from, to)).thenReturn(List.of(record));

        List<AttendanceRecord> result = attendanceService.getUserAttendance("000009", from, to);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo("01633264162836330711");
        assertThat(result.get(0).getUserName()).isEqualTo("张琦");
    }
}
