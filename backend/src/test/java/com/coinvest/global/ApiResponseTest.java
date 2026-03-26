package com.coinvest.global;

import com.coinvest.global.common.ApiResponse;
import com.coinvest.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse 테스트")
class ApiResponseTest {

	@Test
	@DisplayName("성공 응답 (데이터 포함)")
	void testSuccessWithData() {
		// given
		String data = "test data";

		// when
		ApiResponse<String> response = ApiResponse.success(data);

		// then
		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getData()).isEqualTo(data);
		assertThat(response.getMessage()).isNull();
	}

	@Test
	@DisplayName("성공 응답 (데이터 없음)")
	void testSuccessWithoutData() {
		// when
		ApiResponse<Void> response = ApiResponse.success();

		// then
		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getData()).isNull();
		assertThat(response.getMessage()).isNull();
	}

	@Test
	@DisplayName("실패 응답 (ErrorCode)")
	void testFailureWithErrorCode() {
		// when
		ApiResponse<Void> response = ApiResponse.failure(ErrorCode.PORTFOLIO_NOT_FOUND);

		// then
		assertThat(response.isSuccess()).isFalse();
		assertThat(response.getData()).isNull();
		assertThat(response.getMessage()).isEqualTo(ErrorCode.PORTFOLIO_NOT_FOUND.getMessage());
	}

	@Test
	@DisplayName("실패 응답 (커스텀 메시지)")
	void testFailureWithCustomMessage() {
		// given
		String customMessage = "Custom error message";

		// when
		ApiResponse<Void> response = ApiResponse.failure(customMessage);

		// then
		assertThat(response.isSuccess()).isFalse();
		assertThat(response.getData()).isNull();
		assertThat(response.getMessage()).isEqualTo(customMessage);
	}
}
