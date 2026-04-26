package com.smartlab.erp.repository;

import com.smartlab.erp.entity.DingTalkUserDirectory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DingTalkUserDirectoryRepository extends JpaRepository<DingTalkUserDirectory, String> {
}
