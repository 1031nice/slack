package com.slack.common.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * HTTP 응답 생성을 위한 헬퍼 클래스
 * Controller에서 반복되는 ResponseEntity 생성 패턴을 간소화합니다.
 */
public class ResponseHelper {

    private ResponseHelper() {
        // 유틸리티 클래스이므로 인스턴스화 방지
    }

    /**
     * 201 Created 응답을 생성합니다.
     * 
     * @param body 응답 본문
     * @param <T> 응답 타입
     * @return ResponseEntity with 201 Created status
     */
    public static <T> ResponseEntity<T> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * 200 OK 응답을 생성합니다.
     * 
     * @param body 응답 본문
     * @param <T> 응답 타입
     * @return ResponseEntity with 200 OK status
     */
    public static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok(body);
    }
}

