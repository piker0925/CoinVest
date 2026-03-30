package com.coinvest.price.service;

import java.util.List;

/**
 * Upbit WebSocket 클라이언트 인터페이스.
 */
public interface UpbitWebSocketClient {

    /**
     * WebSocket 연결 시작.
     */
    void connect();

    /**
     * WebSocket 연결 종료.
     */
    void disconnect();

    /**
     * 특정 마켓 목록 구독 추가/변경.
     * @param marketCodes 구독할 마켓 코드 리스트 (예: ["KRW-BTC", "KRW-ETH"])
     */
    void subscribe(List<String> marketCodes);

    /**
     * 현재 연결 상태 확인.
     */
    boolean isConnected();
}
