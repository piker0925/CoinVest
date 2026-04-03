package com.coinvest.portfolio.domain;

import com.coinvest.global.util.EncryptionUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Webhook URL을 DB 저장 시 암호화하고 조회 시 복호화하는 컨버터
 */
@Converter
public class WebhookUrlConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return EncryptionUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return EncryptionUtil.decrypt(dbData);
    }
}
