package com.smartlab.erp.service;

import com.smartlab.erp.dto.ResearchInitiateRequest;
import com.smartlab.erp.entity.ResearchProjectProfile;
import com.smartlab.erp.entity.SysProject;
import com.smartlab.erp.entity.SysProjectMember;
import com.smartlab.erp.entity.User;
import com.smartlab.erp.exception.PermissionDeniedException;
import com.smartlab.erp.repository.MiddlewareAssetRepository;
import com.smartlab.erp.repository.MiddlewareRoyaltyRosterRepository;
import com.smartlab.erp.repository.ResearchProjectProfileRepository;
import com.smartlab.erp.repository.SysProjectMemberRepository;
import com.smartlab.erp.repository.SysProjectRepository;
import com.smartlab.erp.repository.UserRepository;
import com.smartlab.erp.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchFlowServiceTest {

    @Mock
    private SysProjectRepository projectRepository;
    @Mock
    private SysProjectMemberRepository projectMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ResearchProjectProfileRepository researchProjectProfileRepository;
    @Mock
    private MiddlewareAssetRepository middlewareAssetRepository;
    @Mock
    private MiddlewareRoyaltyRosterRepository middlewareRoyaltyRosterRepository;

    @InjectMocks
    private ResearchFlowService researchFlowService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsResearchRoleToInitiateResearch() {
        ReflectionTestUtils.setField(researchFlowService, "researchInitiatorsConfig", "焦淼,胡军,任涛,余文清");
        setCurrentUser("000001", "普通科研用户", "RESEARCH");

        User initiator = buildUser("000001", "普通科研用户", "RESEARCH");
        User member = buildUser("000002", "成员A", "DEV");
        mockHappyPath(initiator, member);

        SysProject project = researchFlowService.initiateResearch(buildRequest());

        assertThat(project).isNotNull();
        verify(projectRepository).save(any(SysProject.class));
        verify(researchProjectProfileRepository).save(any(ResearchProjectProfile.class));
        verify(projectMemberRepository).save(any(SysProjectMember.class));
    }

    @Test
    void allowsWhitelistedBusinessUserToInitiateResearch() {
        ReflectionTestUtils.setField(researchFlowService, "researchInitiatorsConfig", "焦淼,胡军,任涛,余文清");
        setCurrentUser("000003", "焦淼", "BUSINESS");

        User initiator = buildUser("000003", "焦淼", "BUSINESS");
        User member = buildUser("000004", "成员B", "DEV");
        mockHappyPath(initiator, member);

        SysProject project = researchFlowService.initiateResearch(buildRequest());

        assertThat(project).isNotNull();
        verify(projectRepository).save(any(SysProject.class));
    }

    @Test
    void rejectsNonResearchUserOutsideWhitelist() {
        ReflectionTestUtils.setField(researchFlowService, "researchInitiatorsConfig", "焦淼,胡军,任涛,余文清");
        setCurrentUser("000005", "普通商务", "BUSINESS");

        assertThatThrownBy(() -> researchFlowService.initiateResearch(buildRequest()))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("授权白名单用户");

        verify(projectRepository, never()).save(any(SysProject.class));
        verify(researchProjectProfileRepository, never()).save(any(ResearchProjectProfile.class));
    }

    private void mockHappyPath(User initiator, User extraMember) {
        when(userRepository.findById(initiator.getUserId())).thenReturn(Optional.of(initiator));
        when(userRepository.findById(extraMember.getUserId())).thenReturn(Optional.of(extraMember));
        when(projectRepository.save(any(SysProject.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(researchProjectProfileRepository.save(any(ResearchProjectProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectMemberRepository.findByProjectIdAndUserUserId(any(), any())).thenReturn(Optional.empty());
        when(projectMemberRepository.save(any(SysProjectMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ResearchInitiateRequest buildRequest() {
        ResearchInitiateRequest request = new ResearchInitiateRequest();
        request.setIdea("新型科研项目");
        request.setInnovationPoint("创新点说明");
        request.setBudget(BigDecimal.valueOf(1000));
        request.setCoreMemberIds(List.of("000002"));
        return request;
    }

    private User buildUser(String userId, String username, String role) {
        return User.builder()
                .userId(userId)
                .username(username)
                .password("encoded")
                .role(role)
                .active(true)
                .build();
    }

    private void setCurrentUser(String userId, String username, String role) {
        UserPrincipal principal = new UserPrincipal(
                userId,
                username,
                username,
                role,
                username + "@example.com",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
