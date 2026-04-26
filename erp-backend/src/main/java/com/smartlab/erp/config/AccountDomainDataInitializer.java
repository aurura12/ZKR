package com.smartlab.erp.config;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.enums.AccountDomain;
import com.smartlab.erp.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(1)
public class AccountDomainDataInitializer implements ApplicationRunner {

    static final AccountDomain DOMAIN_ERP = AccountDomain.ERP;
    static final AccountDomain DOMAIN_FINANCE = AccountDomain.FINANCE;

    private final UserRepository userRepository;
    private final Set<String> financeUserAllowlist;

    public AccountDomainDataInitializer(
            UserRepository userRepository,
            @Value("${auth.account-domain.finance-usernames:}") String financeUsernames) {
        this.userRepository = userRepository;
        this.financeUserAllowlist = parseAllowlist(financeUsernames);
    }

    @Override
    public void run(ApplicationArguments args) {
        backfillMissingAccountDomains();
    }

    @Transactional
    public void backfillMissingAccountDomains() {
        List<User> usersToUpdate;
        try {
            usersToUpdate = userRepository.findAll().stream()
                    .filter(this::isMissingAccountDomain)
                    .peek(user -> user.setAccountDomain(resolveAccountDomain(user)))
                    .toList();
        } catch (InvalidDataAccessResourceUsageException ex) {
            if (isMissingUserTable(ex)) {
                return;
            }
            throw ex;
        }

        if (!usersToUpdate.isEmpty()) {
            userRepository.saveAll(usersToUpdate);
        }
    }

    private boolean isMissingUserTable(InvalidDataAccessResourceUsageException ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("sys_user") && message.toLowerCase(Locale.ROOT).contains("does not exist")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isMissingAccountDomain(User user) {
        return user.getAccountDomain() == null;
    }

    private AccountDomain resolveAccountDomain(User user) {
        String username = user.getUsername();
        if (username != null && financeUserAllowlist.contains(username.trim().toLowerCase(Locale.ROOT))) {
            return DOMAIN_FINANCE;
        }
        return DOMAIN_ERP;
    }

    private Set<String> parseAllowlist(String financeUsernames) {
        if (financeUsernames == null || financeUsernames.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(financeUsernames.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
