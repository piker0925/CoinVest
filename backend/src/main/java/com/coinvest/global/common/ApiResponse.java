package com.coinvest.global.common;

import com.coinvest.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 모든 API 응답의 표준 래퍼.
 * 성공/실패 여부와 관계없이 동일한 구조를 사용.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
	private final boolean success;
	private final T data;
	private final String message;

	/**
	 * 성공 응답 (데이터 포함)
	 */
	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	/**
	 * 성공 응답 (데이터 없음)
	 */
	public static <Void> ApiResponse<Void> success() {
		return new ApiResponse<>(true, null, null);
	}

	/**
	 * 실패 응답 (ErrorCode 기반)
	 */
	public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
		return new ApiResponse<>(false, null, errorCode.getMessage());
	}

	/**
	 * 실패 응답 (커스텀 메시지)
	 */
	public static <T> ApiResponse<T> failure(String message) {
		return new ApiResponse<>(false, null, message);
	}
}
