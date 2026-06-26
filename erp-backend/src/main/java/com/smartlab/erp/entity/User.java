package com.smartlab.erp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartlab.erp.enums.AccountDomain;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "sys_user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User implements UserDetails {

    @Id
    @Column(name = "user_id", nullable = false, length = 64)
    // 🟢 显式告诉 Jackson：不管字段叫啥，JSON 里统统叫 "userId"
    @JsonProperty("userId")
    private String userId;

    @Column(nullable = false, unique = true)
    private String username;

    @JsonIgnore // 🔴 密码绝对不能序列化给前端，防止泄露
    @Column(name = "password_hash", nullable = false)
    private String password;

    private String name;
    private String email;
    private String role;
    private String avatar;

    @Column(name = "hidden_avatar", nullable = false)
    @Builder.Default
    private Boolean hiddenAvatar = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_domain", length = 32)
    @Builder.Default
    private AccountDomain accountDomain = AccountDomain.ERP;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(name = "daily_wage", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal dailyWage = new BigDecimal("300.00");

    @Column(name = "is_server_ops_admin")
    @Builder.Default
    private Boolean serverOpsAdmin = false;

    @Column(name = "id_number", length = 18)
    private String idNumber;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account", length = 30)
    private String bankAccount;

    @Column(name = "ethnicity", length = 20)
    private String ethnicity;

    @Column(name = "position", length = 50)
    private String position;

    @Column(name = "is_part_time")
    @Builder.Default
    private Boolean partTime = false;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    @Column(name = "payment_entity", length = 100)
    @Builder.Default
    private String paymentEntity = "国科九天";

    @Column(name = "school_department", length = 200)
    private String schoolDepartment;

    @Column(name = "address", length = 300)
    private String address;

    @Transient
    @Builder.Default
    private List<UserBadge> badges = new ArrayList<>();

    // --- 重点：统一 ID 访问器 ---

    /**
     * 为了兼容你之前代码中可能存在的 getId() 调用
     * 我们加上 @JsonIgnore，防止 JSON 中出现重复的 id 字段
     */
    @JsonIgnore
    public String getId() {
        return userId;
    }

    // --- UserDetails 接口实现 ---

    @Override
    @JsonIgnore // 接口方法不需要变成 JSON
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String r = (this.role == null) ? "RESEARCH" : this.role;
        return List.of(new SimpleGrantedAuthority("ROLE_" + r));
    }

    @Override @JsonIgnore public boolean isAccountNonExpired() { return true; }
    @Override @JsonIgnore public boolean isAccountNonLocked() { return true; }
    @Override @JsonIgnore public boolean isCredentialsNonExpired() { return true; }
    @Override @JsonIgnore public boolean isEnabled() { return Boolean.TRUE.equals(this.active); }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
