package com.smartlab.erp.repository;

import com.smartlab.erp.entity.SysProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysProjectMemberRepository extends JpaRepository<SysProjectMember, Long> {

    /**
     * 查找某项目的所有成员
     */
    List<SysProjectMember> findByProjectId(String projectId);

    @Query("SELECT m FROM SysProjectMember m JOIN FETCH m.user WHERE m.projectId = :projectId")
    List<SysProjectMember> findByProjectIdWithUser(@Param("projectId") String projectId);

    @Query("SELECT m FROM SysProjectMember m JOIN FETCH m.user WHERE m.projectId IN :projectIds")
    List<SysProjectMember> findByProjectIdInWithUser(@Param("projectIds") List<String> projectIds);

    @Query("SELECT m FROM SysProjectMember m JOIN FETCH m.user WHERE m.user.userId = :userId")
    List<SysProjectMember> findByUserUserIdWithUser(@Param("userId") String userId);

    /**
     * ✅ 核心修复：添加这个缺失的方法
     * 根据 "项目ID" 和 "用户ID" 查找单个成员记录
     * 用于权限判断 (RbacService)
     */
    Optional<SysProjectMember> findByProjectIdAndUserUserId(String projectId, String userId);

    /**
     * 检查是否存在 (用于防止重复拉人)
     */
    boolean existsByProjectIdAndUserUserId(String projectId, String userId);

    /**
     * 删除项目时级联删除成员
     */
    @Modifying
    void deleteByProjectId(String projectId);

    @Modifying
    long deleteByProjectIdAndUserUserId(String projectId, String userId);
}
