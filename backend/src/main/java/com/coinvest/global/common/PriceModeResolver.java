package com.coinvest.global.common;

import com.coinvest.auth.domain.UserRole;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Collection;

/**
 * UserRole 기반 PriceMode 결정 유틸리티.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriceModeResolver {

    public static PriceMode resolve(UserRole role) {
        if (role == UserRole.ADMIN) {
            return PriceMode.LIVE;
        }
        return PriceMode.DEMO; // USER, BOT 모두 DEMO
    }

    /**
     * Spring Security의 권한 목록에서 PriceMode 추출
     */
    public static PriceMode resolveFromAuthorities(Collection<String> authorities) {
        if (authorities.contains("ROLE_ADMIN")) {
            return PriceMode.LIVE;
        }
        return PriceMode.DEMO;
    }
}
