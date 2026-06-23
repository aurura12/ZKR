package com.smartlab.erp.projectfile;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "project_file_mapping")
@Data
public class ProjectFileMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "source_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ProjectFileSourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "display_name", nullable = false, length = 300)
    private String displayName;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
