package com.slack.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResponseHelper 단위 테스트")
class ResponseHelperTest {

    @Test
    @DisplayName("created 메서드는 201 Created 상태 코드를 반환한다")
    void created_Returns201Status() {
        // given
        String body = "test response";

        // when
        ResponseEntity<String> result = ResponseHelper.created(body);

        // then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isEqualTo(body);
    }

    @Test
    @DisplayName("created 메서드는 null body를 처리할 수 있다")
    void created_WithNullBody() {
        // when
        ResponseEntity<String> result = ResponseHelper.created(null);

        // then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNull();
    }

    @Test
    @DisplayName("ok 메서드는 200 OK 상태 코드를 반환한다")
    void ok_Returns200Status() {
        // given
        String body = "test response";

        // when
        ResponseEntity<String> result = ResponseHelper.ok(body);

        // then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(body);
    }

    @Test
    @DisplayName("ok 메서드는 null body를 처리할 수 있다")
    void ok_WithNullBody() {
        // when
        ResponseEntity<String> result = ResponseHelper.ok(null);

        // then
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNull();
    }

    @Test
    @DisplayName("created 메서드는 다양한 타입의 body를 처리할 수 있다")
    void created_WithDifferentTypes() {
        // given
        Integer intBody = 123;
        Long longBody = 456L;

        // when
        ResponseEntity<Integer> intResult = ResponseHelper.created(intBody);
        ResponseEntity<Long> longResult = ResponseHelper.created(longBody);

        // then
        assertThat(intResult.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(intResult.getBody()).isEqualTo(123);
        assertThat(longResult.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(longResult.getBody()).isEqualTo(456L);
    }

    @Test
    @DisplayName("ok 메서드는 다양한 타입의 body를 처리할 수 있다")
    void ok_WithDifferentTypes() {
        // given
        Integer intBody = 123;
        Long longBody = 456L;

        // when
        ResponseEntity<Integer> intResult = ResponseHelper.ok(intBody);
        ResponseEntity<Long> longResult = ResponseHelper.ok(longBody);

        // then
        assertThat(intResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(intResult.getBody()).isEqualTo(123);
        assertThat(longResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(longResult.getBody()).isEqualTo(456L);
    }
}

