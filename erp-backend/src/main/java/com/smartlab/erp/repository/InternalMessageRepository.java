package com.smartlab.erp.repository;

import com.smartlab.erp.entity.InternalMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InternalMessageRepository extends JpaRepository<InternalMessage, Long> {
    List<InternalMessage> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);

    long countByRecipientUserIdAndReadFalse(String recipientUserId);

    @Modifying
    @Query("UPDATE InternalMessage m SET m.read = true WHERE m.recipientUserId = :userId AND m.read = false")
    int markAllReadByUserId(@Param("userId") String userId);
}
