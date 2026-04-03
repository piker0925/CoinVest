package com.coinvest.global.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptionUtilTest {

    @BeforeAll
    static void setup() {
        // Reflection을 통해 정적 필드에 테스트 키 주입
        EncryptionUtil util = new EncryptionUtil();
        ReflectionTestUtils.setField(util, "key", "12345678901234567890123456789012");
        util.init();
    }

    @Test
    @DisplayName("Webhook URL 암호화 및 복호화 라운드트립 성공")
    void encryptAndDecryptSuccess() {
        // given
        String originalUrl = "https://discord.com/api/webhooks/123456/abcdef";

        // when
        String encrypted = EncryptionUtil.encrypt(originalUrl);
        String decrypted = EncryptionUtil.decrypt(encrypted);

        // then
        assertThat(encrypted).isNotEqualTo(originalUrl);
        assertThat(decrypted).isEqualTo(originalUrl);
    }

    @Test
    @DisplayName("null 입력 시 null 반환")
    void handleNull() {
        assertThat(EncryptionUtil.encrypt(null)).isNull();
        assertThat(EncryptionUtil.decrypt(null)).isNull();
    }
}
