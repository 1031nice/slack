package com.slack.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 메시지 시퀀스 번호 생성 서비스
 * 
 * 각 채널별로 고유한 시퀀스 번호를 생성하여
 * 메시지 순서 보장 및 ACK 처리를 위한 식별자로 사용합니다.
 */
@Service
public class MessageSequenceService {

    // 채널별 시퀀스 번호 (간단한 구현, 실제로는 Redis나 DB를 사용하는 것이 좋음)
    // v0.3에서는 단순하게 메모리 기반으로 구현
    private final AtomicLong globalSequence = new AtomicLong(0);

    /**
     * 다음 시퀀스 번호를 생성합니다.
     * 
     * @return 새로운 시퀀스 번호
     */
    public Long getNextSequenceNumber() {
        return globalSequence.incrementAndGet();
    }

    /**
     * 현재 시퀀스 번호를 반환합니다 (증가하지 않음).
     * 
     * @return 현재 시퀀스 번호
     */
    public Long getCurrentSequenceNumber() {
        return globalSequence.get();
    }
}

