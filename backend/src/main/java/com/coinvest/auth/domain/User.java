package com.coinvest.auth.domain;

import com.coinvest.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 사용자 엔티티.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    /**
     * 회원 가입 시 기본 상태 설정
     */
    @PrePersist
    public void prePersist() {
        if (this.role == null) this.role = UserRole.USER;
        if (this.authProvider == null) this.authProvider = AuthProvider.LOCAL;
        this.isActive = true;
    }

    /**
     * Soft Delete 처리
     */
    public void deactivate() {
        this.isActive = false;
    }
}
