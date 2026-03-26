package com.coinvest.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전역 에러 코드.
 * HTTP 상태 코드와 함께 도메인별 에러 메시지를 정의.
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
	// 404 Not Found (리소스 없음 또는 접근 권한 없음 - IDOR 방어)
	PORTFOLIO_NOT_FOUND("포트폴리오를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
	ALERT_NOT_FOUND("알림을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
	USER_NOT_FOUND("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

	// 401 Unauthorized (인증 실패)
	AUTH_INVALID_TOKEN("유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED),
	AUTH_EMAIL_DUPLICATE("이미 사용 중인 이메일입니다", HttpStatus.UNAUTHORIZED),

	// 400 Bad Request (입력값 오류)
	COMMON_INVALID_INPUT("입력값이 유효하지 않습니다", HttpStatus.BAD_REQUEST),

	// 500 Internal Server Error (시스템 오류)
	COMMON_INTERNAL_ERROR("시스템 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String message;
	private final HttpStatus httpStatus;
}
