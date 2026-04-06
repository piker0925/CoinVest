package com.coinvest.global.util;

/**
 * 테스트 시 시간 제어를 위한 인터페이스.
 */
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
