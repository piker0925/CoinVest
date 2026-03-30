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
	PORTFOLIO_LIMIT_EXCEEDED("사용자당 최대 5개의 포트폴리오만 생성할 수 있습니다", HttpStatus.BAD_REQUEST),
	PORTFOLIO_INVALID_WEIGHT("자산 비중의 합은 반드시 100%여야 합니다", HttpStatus.BAD_REQUEST),
	ALERT_NOT_FOUND("알림을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
	USER_NOT_FOUND("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

	// Trading
	TRADING_INSUFFICIENT_BALANCE("가용 잔고가 부족합니다", HttpStatus.BAD_REQUEST),
	TRADING_INSUFFICIENT_QUANTITY("보유 수량이 부족합니다", HttpStatus.BAD_REQUEST),
	TRADING_INVALID_ORDER_PRICE("지정가 주문 시 가격은 필수이며 0보다 커야 합니다", HttpStatus.BAD_REQUEST),
	TRADING_ORDER_NOT_FOUND("주문을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
	TRADING_ORDER_NOT_CANCELABLE("취소 가능한 상태의 주문이 아닙니다", HttpStatus.BAD_REQUEST),

	// 409 Conflict (데이터 충돌)
	AUTH_EMAIL_DUPLICATE("이미 사용 중인 이메일입니다", HttpStatus.CONFLICT),

	// 401 Unauthorized (인증 실패)
	AUTH_INVALID_TOKEN("유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED),
	AUTH_INVALID_CREDENTIALS("이메일 또는 비밀번호가 일치하지 않습니다", HttpStatus.UNAUTHORIZED),

	// 403 Forbidden (권한 없음)
	AUTH_FORBIDDEN("접근 권한이 없습니다", HttpStatus.FORBIDDEN),

	// 429 Too Many Requests (요청 초과)
	AUTH_TOO_MANY_ATTEMPTS("로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요", HttpStatus.TOO_MANY_REQUESTS),

	// 400 Bad Request (입력값 오류)
	COMMON_INVALID_INPUT("입력값이 유효하지 않습니다", HttpStatus.BAD_REQUEST),

	// 500 Internal Server Error (시스템 오류)
	COMMON_INTERNAL_ERROR("시스템 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);

	private final String message;
	private final HttpStatus httpStatus;
}
