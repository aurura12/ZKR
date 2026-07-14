package com.smartlab.erp.config;

import com.smartlab.erp.entity.FlowType;
import com.smartlab.erp.entity.ProjectType;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyProjectInitializer {

    public static final String COMPANY_PROJECT_ID = "COMPANY";

    private final SysProjectRepository projectRepository;
    private final UserRepository userRepository;

    @PostConstruct
    @Transactional
    public void init() {
        if (projectRepository.findById(COMPANY_PROJECT_ID).isPresent()) {
            log.info("[CompanyProject] Company project already exists, skipping initialization.");
            return;
        }

        var adminUser = userRepository.findById("000010");
        if (adminUser.isEmpty()) {
            adminUser = userRepository.findAll().stream().findFirst();
        }
        if (adminUser.isEmpty()) {
            log.warn("[CompanyProject] No users found in system, cannot create company project.");
            return;
        }

        SysProject company = SysProject.builder()
                .projectId(COMPANY_PROJECT_ID)
                .name("国科九天公司")
                .projectType(ProjectType.SELF_USE)
                .flowType(FlowType.PROJECT)
                .projectStatus(com.smartlab.erp.entity.ProjectStatus.IMPLEMENTING)
                .manager(adminUser.get())
                .build();

        projectRepository.save(company);
        log.info("[CompanyProject] Created company project: {} ({})", company.getProjectId(), company.getName());
    }
}
