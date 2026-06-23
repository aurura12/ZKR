package com.smartlab.erp.projectfile;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "project_file_folder")
@Data
public class ProjectFileFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 500)
    private String path;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
