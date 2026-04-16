
package com.smartlab.erp.repository;
import com.smartlab.erp.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(String userId);

    List<UserRole> findByRoleAndIsLeaderTrue(String role);

    Optional<UserRole> findByUserIdAndRole(String userId, String role);

    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.user WHERE ur.role = :role AND ur.isLeader = true")
    List<UserRole> findLeadersByRoleWithUser(@Param("role") String role);

    @Query("SELECT ur FROM UserRole ur JOIN FETCH ur.user WHERE ur.role = :role")
    List<UserRole> findByRoleWithUser(@Param("role") String role);

    boolean existsByUserIdAndRoleAndIsLeaderTrue(String userId, String role);
}
