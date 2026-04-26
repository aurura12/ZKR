package com.smartlab.erp.repository;

import com.smartlab.erp.entity.WorkflowMemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class WorkflowMemberRoleRepositoryTest {

    @Autowired
    private WorkflowMemberRoleRepository repository;

    @Test
    void allowsSameUserWithDifferentRolesButRejectsDuplicateRole() {
        WorkflowMemberRole data = repository.saveAndFlush(WorkflowMemberRole.builder()
                .workflowType("PROJECT")
                .workflowId("p-1")
                .userId("000027")
                .role("DATA")
                .build());

        WorkflowMemberRole dev = repository.saveAndFlush(WorkflowMemberRole.builder()
                .workflowType("PROJECT")
                .workflowId("p-1")
                .userId("000027")
                .role("DEV")
                .build());

        assertThat(data.getId()).isNotNull();
        assertThat(dev.getId()).isNotNull();
        assertThat(data.getCreatedAt()).isInstanceOf(Instant.class);
        assertThat(data.getUpdatedAt()).isInstanceOf(Instant.class);
        assertThat(dev.getCreatedAt()).isInstanceOf(Instant.class);
        assertThat(dev.getUpdatedAt()).isInstanceOf(Instant.class);

        assertThatThrownBy(() -> repository.saveAndFlush(WorkflowMemberRole.builder()
                .workflowType("PROJECT")
                .workflowId("p-1")
                .userId("000027")
                .role("DATA")
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsExactWorkflowRole() {
        repository.saveAndFlush(WorkflowMemberRole.builder()
                .workflowType("PRODUCT")
                .workflowId("p-2")
                .userId("000028")
                .role("PROMOTION_IC")
                .build());

        assertThat(repository.findByWorkflowTypeAndWorkflowIdAndUserIdAndRole("PRODUCT", "p-2", "000028", "PROMOTION_IC"))
                .isPresent();
    }
}
