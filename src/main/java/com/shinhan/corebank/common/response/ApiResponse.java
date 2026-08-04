package com.shinhan.corebank.common.response;

// 성공 응답 전용 봉투. 실패 응답은 ErrorResponse 가 전담한다
public record ApiResponse<T>(String code, String message, T data) {

    private static final String SUCCESS_CODE = "0000";
    private static final String SUCCESS_MESSAGE = "정상 처리되었습니다.";

    // 데이터가 있는 성공 응답
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    // 데이터가 있는 성공 응답 + 커스텀 메시지 (예: "자동이체가 등록되었습니다.")
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(SUCCESS_CODE, message, data);
    }

    // 반환할 데이터가 없는 성공 응답. 204 대신 200 + data:null 을 사용
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }
}
