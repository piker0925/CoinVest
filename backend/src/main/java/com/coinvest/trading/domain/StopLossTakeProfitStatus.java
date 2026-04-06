package com.coinvest.trading.domain;

public enum StopLossTakeProfitStatus {
    ACTIVE,      // 감시 중
    PROCESSING,  // 조건 도달하여 주문 처리 중
    EXECUTED,    // 주문 실행 완료
    FAILED,      // 주문 실행 실패
    CANCELLED    // 사용자가 취소
}
