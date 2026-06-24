package com.smartlab.erp.projectfile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectFileFolderRepository extends JpaRepository<ProjectFileFolder, Long> {
    List<ProjectFileFolder> findByProjectId(String projectId);

    Optional<ProjectFileFolder> findByProjectIdAndPath(String projectId, String path);

    boolean existsByProjectIdAndParentIdAndName(String projectId, Long parentId, String name);

    List<ProjectFileFolder> findByProjectIdAndParentId(String projectId, Long parentId);
}
