package com.coinvest.global.exception;

import lombok.Getter;

@Getter
public class CircuitBreakerException extends BusinessException {
    public CircuitBreakerException(ErrorCode errorCode) {
        super(errorCode);
    }
}
