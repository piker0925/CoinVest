package com.coinvest.trading.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitters {

    // userId -> SseEmitter
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter add(Long userId, SseEmitter emitter) {
        this.emitters.put(userId, emitter);

        // OOM 및 좀비 커넥션 방지를 위한 명시적 제거 콜백
        emitter.onCompletion(() -> {
            log.debug("SSE completed for user: {}", userId);
            this.emitters.remove(userId);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for user: {}", userId);
            emitter.complete();
            this.emitters.remove(userId);
        });
        emitter.onError((e) -> {
            log.debug("SSE error for user: {}", userId, e);
            emitter.completeWithError(e);
            this.emitters.remove(userId);
        });

        return emitter;
    }

    public void remove(Long userId) {
        this.emitters.remove(userId);
    }

    public SseEmitter get(Long userId) {
        return this.emitters.get(userId);
    }

    /**
     * 특정 사용자에게 SSE 이벤트 발송.
     * Kafka 재시도 지옥 방지를 위해 IOException을 내부에서 catch하여 로컬에서 제거 처리함.
     */
    public void send(Long userId, String name, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name(name)
                    .data(data));
        } catch (IOException e) {
            log.debug("Failed to send SSE to user {}: {}. Removing emitter.", userId, e.getMessage());
            emitter.complete();
            emitters.remove(userId);
        }
    }

    /**
     * Nginx 504 Gateway Timeout 방지를 위한 30초 주기 더미 Heartbeat 발송
     */
    @Scheduled(fixedDelay = 30000)
    public void sendHeartbeat() {
        emitters.forEach((userId, emitter) -> {
            try {
                // 더미 데이터 혹은 빈 이벤트 전송
                emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
            } catch (IOException e) {
                // 전송 실패 (클라이언트 비정상 연결 종료) -> 제거
                emitter.completeWithError(e);
                emitters.remove(userId);
                log.debug("Removed dead SSE emitter during heartbeat for user {}", userId);
            }
        });
    }
}
