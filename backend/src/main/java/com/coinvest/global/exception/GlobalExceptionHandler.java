package com.coinvest.global.exception;

import com.coinvest.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * 전역 예외 처리.
 * 모든 도메인 예외를 일관된 방식으로 처리하여 클라이언트에게 표준화된 에러 응답을 제공.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * 비즈니스 로직 예외 처리 (ErrorCode 기반)
	 * IDOR 방어: 리소스 없음과 접근 권한 없음을 동일한 404로 반환
	 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<?> handleBusinessException(BusinessException e) {
		log.warn("Business exception: {}", e.getErrorCode(), e);
		return ResponseEntity
			.status(e.getErrorCode().getHttpStatus())
			.body(ApiResponse.failure(e.getErrorCode()));
	}

	/**
	 * 입력값 검증 실패 (Spring Validation)
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
		log.warn("Validation failed: {}", e.getBindingResult().getFieldErrors());
		return ResponseEntity
			.status(400)
			.body(ApiResponse.failure(ErrorCode.COMMON_INVALID_INPUT));
	}

	/**
	 * orElseThrow() 미처리로 인한 NoSuchElementException → 404로 변환
	 * 500 대신 명시적 Not Found를 반환하여 클라이언트 혼란 방지
	 */
	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<?> handleNoSuchElement(NoSuchElementException e) {
		log.warn("Resource not found: {}", e.getMessage());
		return ResponseEntity
			.status(404)
			.body(ApiResponse.failure(ErrorCode.COMMON_NOT_FOUND));
	}

	/**
	 * 예상치 못한 시스템 오류
	 * 스택 트레이스를 로그에만 기록하고 클라이언트에게는 일반 메시지 반환
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<?> handleGeneralException(Exception e) {
		log.error("Unexpected exception", e);
		return ResponseEntity
			.status(500)
			.body(ApiResponse.failure(ErrorCode.COMMON_INTERNAL_ERROR));
	}
}
