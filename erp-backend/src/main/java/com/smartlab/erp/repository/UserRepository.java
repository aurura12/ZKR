package com.smartlab.erp.repository;

import com.smartlab.erp.entity.User;
import com.smartlab.erp.enums.AccountDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> { // 主键是 String

    boolean existsByUsername(String username);

    boolean existsByEmailAndAccountDomain(String email, AccountDomain accountDomain);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailAndAccountDomain(String email, AccountDomain accountDomain);

    Optional<User> findByPhone(String phone);

    // 🟢 核心补充：查询目前最大的 ID 用于生成下一个
    @Query("SELECT MAX(u.userId) FROM User u")
    Optional<String> findMaxUserId();
}
