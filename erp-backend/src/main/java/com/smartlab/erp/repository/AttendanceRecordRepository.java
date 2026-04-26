package com.smartlab.erp.repository;

import com.smartlab.erp.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByUserIdAndWorkDateOrderByUserCheckTimeAsc(String userId, LocalDate workDate);

    List<AttendanceRecord> findByWorkDateBetweenOrderByWorkDateAscUserIdAsc(LocalDate from, LocalDate to);

    List<AttendanceRecord> findByUserIdOrderByWorkDateDesc(String userId);

    boolean existsByUserIdAndUserCheckTimeAndCheckType(String userId, java.time.Instant userCheckTime, String checkType);
}
