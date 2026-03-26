package com.coinvest.global.exception;

/**
 * 리소스를 찾을 수 없거나 접근 권한이 없을 때 발생.
 * 404 응답을 반환하여 IDOR 공격 방어.
 *
 * 사용 사례:
 * - 포트폴리오 ID로 조회했으나 리소스 없음
 * - 포트폴리오는 존재하지만 현재 사용자의 소유가 아님 (동일한 404 반환)
 */
public class ResourceNotFoundException extends BusinessException {
	public ResourceNotFoundException(ErrorCode errorCode) {
		super(errorCode);
	}

	public ResourceNotFoundException(ErrorCode errorCode, String customMessage) {
		super(errorCode, customMessage);
	}
}
