package com.smartlab.erp.service;

import com.smartlab.erp.entity.AttendanceRecord;
import com.smartlab.erp.entity.DingTalkUserDirectory;
import com.smartlab.erp.repository.AttendanceRecordRepository;
import com.smartlab.erp.repository.DingTalkUserDirectoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DingTalkUserDirectoryService {

    private final DingTalkUserDirectoryRepository directoryRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Transactional(readOnly = true)
    public Map<String, DingTalkUserDirectory> loadDirectoryMap() {
        return directoryRepository.findAll().stream()
                .filter(item -> item.getUserId() != null && !item.getUserId().isBlank())
                .collect(Collectors.toMap(DingTalkUserDirectory::getUserId, Function.identity(), (a, b) -> a));
    }

    @Transactional
    public int backfillAttendanceNames() {
        Map<String, DingTalkUserDirectory> directoryMap = loadDirectoryMap();
        if (directoryMap.isEmpty()) {
            return 0;
        }

        List<AttendanceRecord> records = attendanceRecordRepository.findAll().stream()
                .filter(record -> record.getUserId() != null && !record.getUserId().isBlank())
                .filter(record -> record.getUserName() == null || record.getUserName().isBlank())
                .toList();

        int updated = 0;
        for (AttendanceRecord record : records) {
            DingTalkUserDirectory user = directoryMap.get(record.getUserId());
            if (user != null && user.getName() != null && !user.getName().isBlank()) {
                record.setUserName(user.getName());
                attendanceRecordRepository.save(record);
                updated++;
            }
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public String resolveUserName(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        DingTalkUserDirectory directory = directoryRepository.findById(userId).orElse(null);
        return directory == null ? null : directory.getName();
    }
}
