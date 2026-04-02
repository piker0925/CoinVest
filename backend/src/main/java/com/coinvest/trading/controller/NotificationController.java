package com.coinvest.trading.controller;

import com.coinvest.auth.domain.User;
import com.coinvest.auth.domain.UserRepository;
import com.coinvest.global.exception.BusinessException;
import com.coinvest.global.exception.ErrorCode;
import com.coinvest.trading.service.SseEmitters;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseEmitters sseEmitters;
    private final UserRepository userRepository;

    // 타임아웃을 무제한(또는 충분히 길게, 여기선 1시간) 설정
    private static final Long SSE_TIMEOUT = 3600000L;

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(@AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        sseEmitters.add(user.getId(), emitter);

        try {
            // 연결 즉시 더미 이벤트를 보내주어 클라이언트에게 연결되었음을 알림
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("Connected to Notification Service [User " + user.getId() + "]"));
        } catch (IOException e) {
            sseEmitters.remove(user.getId());
        }

        return ResponseEntity.ok(emitter);
    }
}
