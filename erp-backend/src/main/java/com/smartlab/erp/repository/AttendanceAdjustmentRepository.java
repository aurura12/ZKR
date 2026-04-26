package com.smartlab.erp.repository;

import com.smartlab.erp.entity.AttendanceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AttendanceAdjustmentRepository extends JpaRepository<AttendanceAdjustment, Long> {

    List<AttendanceAdjustment> findByUserIdAndAdjustDate(String userId, LocalDate adjustDate);

    List<AttendanceAdjustment> findByUserIdOrderByCreatedAtDesc(String userId);
}
