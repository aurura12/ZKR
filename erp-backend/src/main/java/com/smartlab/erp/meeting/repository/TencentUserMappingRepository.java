package com.smartlab.erp.meeting.repository;

import com.smartlab.erp.meeting.entity.TencentUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TencentUserMappingRepository extends JpaRepository<TencentUserMapping, String> {

    Optional<TencentUserMapping> findByErpUserId(String erpUserId);

    Optional<TencentUserMapping> findByTencentUserId(String tencentUserId);
}
