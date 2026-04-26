package com.smartlab.erp.config;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountDomainDataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<List<User>> usersCaptor;

    @Test
    void startupDoesNotCrashWhenUserTableIsMissing() {
        AccountDomainDataInitializer initializer = new AccountDomainDataInitializer(userRepository, "");
        when(userRepository.findAll())
                .thenThrow(new InvalidDataAccessResourceUsageException("relation \"sys_user\" does not exist"));

        assertThatCode(() -> initializer.run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();

        verify(userRepository).findAll();
        verify(userRepository, never()).saveAll(any());
    }

    @Test
    void backfillsMissingAccountDomainsToErpByDefaultWhenTableExists() {
        User legacyUser = User.builder()
                .userId("000001")
                .username("legacy-erp-user")
                .password("secret")
                .accountDomain(null)
                .active(true)
                .build();
        AccountDomainDataInitializer initializer = new AccountDomainDataInitializer(userRepository, "");
        when(userRepository.findAll()).thenReturn(List.of(legacyUser));

        initializer.backfillMissingAccountDomains();

        verify(userRepository).saveAll(usersCaptor.capture());
        assertThat(usersCaptor.getValue()).singleElement().satisfies(user -> {
            assertThat(user.getUsername()).isEqualTo("legacy-erp-user");
            assertThat(user.getAccountDomain()).isEqualTo(AccountDomain.ERP);
        });
    }

    @Test
    void backfillsAllowlistedFinanceUsersWhenTableExists() {
        User financePilot = User.builder()
                .userId("000002")
                .username("finance-pilot")
                .password("secret")
                .accountDomain(null)
                .active(true)
                .build();
        AccountDomainDataInitializer initializer = new AccountDomainDataInitializer(userRepository, "finance-pilot,another-user");
        when(userRepository.findAll()).thenReturn(List.of(financePilot));

        initializer.backfillMissingAccountDomains();

        verify(userRepository).saveAll(usersCaptor.capture());
        assertThat(usersCaptor.getValue()).singleElement().satisfies(user -> {
            assertThat(user.getUsername()).isEqualTo("finance-pilot");
            assertThat(user.getAccountDomain()).isEqualTo(AccountDomain.FINANCE);
        });
    }
}
