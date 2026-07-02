package com.smartlab.erp.repository;

import com.smartlab.erp.entity.UserStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserStatusLogRepository extends JpaRepository<UserStatusLog, Long> {

    List<UserStatusLog> findByUserIdOrderByCreatedAtDesc(String userId);
}
