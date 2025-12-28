package com.slack.unread.controller;

import com.slack.user.domain.User;
import com.slack.unread.dto.UnreadMessageResponse;
import com.slack.unread.dto.UnreadsViewResponse;
import com.slack.unread.service.UnreadsViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnreadsController 단위 테스트")
class UnreadsControllerTest {

    @Mock
    private UnreadsViewService unreadsViewService;

    @Mock
    private User user;

    @InjectMocks
    private UnreadsController unreadsController;

    private UnreadsViewResponse testResponse;

    @BeforeEach
    void setUp() {
        UnreadMessageResponse unreadMessage = UnreadMessageResponse.builder()
                .messageId(1000L)
                .channelId(100L)
                .channelName("general")
                .userId(2L)
                .content("Test message")
                .createdAt(LocalDateTime.now())
                .build();

        testResponse = UnreadsViewResponse.builder()
                .unreadMessages(Arrays.asList(unreadMessage))
                .totalCount(1)
                .build();
    }

    @Test
    @DisplayName("기본 파라미터로 unreads를 조회할 수 있다")
    void getUnreads_DefaultParameters_Success() {
        // given
        when(user.getId()).thenReturn(1L);
        lenient().when(unreadsViewService.getUnreads(anyLong(), any(), any())).thenReturn(testResponse);

        // when
        ResponseEntity<UnreadsViewResponse> response = unreadsController.getUnreads(null, null, user);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalCount()).isEqualTo(1);
        assertThat(response.getBody().getUnreadMessages()).hasSize(1);
        verify(unreadsViewService, times(1)).getUnreads(anyLong(), any(), any());
    }

    @Test
    @DisplayName("sort 파라미터를 지정할 수 있다")
    void getUnreads_WithSort_Success() {
        // given
        when(user.getId()).thenReturn(1L);
        lenient().when(unreadsViewService.getUnreads(anyLong(), any(), any())).thenReturn(testResponse);

        // when
        ResponseEntity<UnreadsViewResponse> response = unreadsController.getUnreads("oldest", null, user);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(unreadsViewService, times(1)).getUnreads(anyLong(), any(), any());
    }

    @Test
    @DisplayName("limit 파라미터를 지정할 수 있다")
    void getUnreads_WithLimit_Success() {
        // given
        when(user.getId()).thenReturn(1L);
        lenient().when(unreadsViewService.getUnreads(anyLong(), any(), any())).thenReturn(testResponse);

        // when
        ResponseEntity<UnreadsViewResponse> response = unreadsController.getUnreads(null, 100, user);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(unreadsViewService, times(1)).getUnreads(anyLong(), any(), any());
    }

    @Test
    @DisplayName("모든 파라미터를 지정할 수 있다")
    void getUnreads_WithAllParameters_Success() {
        // given
        when(user.getId()).thenReturn(1L);
        lenient().when(unreadsViewService.getUnreads(anyLong(), any(), any())).thenReturn(testResponse);

        // when
        ResponseEntity<UnreadsViewResponse> response = unreadsController.getUnreads("channel", 50, user);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(unreadsViewService, times(1)).getUnreads(anyLong(), any(), any());
    }

    @Test
    @DisplayName("unread 메시지가 없으면 빈 리스트를 반환한다")
    void getUnreads_NoUnreads_Success() {
        // given
        when(user.getId()).thenReturn(1L);
        UnreadsViewResponse emptyResponse = UnreadsViewResponse.builder()
                .unreadMessages(Collections.emptyList())
                .totalCount(0)
                .build();
        lenient().when(unreadsViewService.getUnreads(anyLong(), any(), any())).thenReturn(emptyResponse);

        // when
        ResponseEntity<UnreadsViewResponse> response = unreadsController.getUnreads(null, null, user);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalCount()).isEqualTo(0);
        assertThat(response.getBody().getUnreadMessages()).isEmpty();
    }
}

