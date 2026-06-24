package com.smartlab.erp.projectfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectFileMappingRepository extends JpaRepository<ProjectFileMapping, Long> {
    List<ProjectFileMapping> findByProjectId(String projectId);

    List<ProjectFileMapping> findByProjectIdAndFolderId(String projectId, Long folderId);

    boolean existsByProjectIdAndSourceTypeAndSourceId(String projectId, ProjectFileSourceType sourceType, String sourceId);

    Optional<ProjectFileMapping> findBySourceTypeAndSourceId(ProjectFileSourceType sourceType, String sourceId);
}
