package com.smartlab.erp.projectfile;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectFileMappingInitializer {

    private final ProjectFileManagerService fileManagerService;

    @PostConstruct
    public void init() {
        try {
            fileManagerService.scanAndInitializeMappings();
        } catch (Exception e) {
            log.error("项目文件映射初始化失败", e);
        }
    }
}
