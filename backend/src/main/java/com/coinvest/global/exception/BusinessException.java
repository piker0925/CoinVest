package com.coinvest.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 예외의 최상위 클래스.
 * 모든 도메인 예외가 이를 상속받아 일관된 에러 처리를 보장.
 */
@Getter
public abstract class BusinessException extends RuntimeException {
	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String customMessage) {
		super(customMessage);
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
	}
}
