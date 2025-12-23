package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.readreceipt.ReadReceipt;
import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.ReadReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReadReceiptService 단위 테스트")
class ReadReceiptServiceTest {

    @Mock
    private ReadReceiptRepository readReceiptRepository;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private ReadReceiptService readReceiptService;

    private User testUser1;
    private User testUser2;
    private Workspace testWorkspace;
    private Channel testChannel;

    private static final Long USER_ID = 1L;
    private static final Long CHANNEL_ID = 100L;
    private static final String TIMESTAMP_ID = "1735046400000050";

    @BeforeEach
    void setUp() throws Exception {
        testUser1 = User.builder()
                .authUserId("auth-1")
                .email("user1@example.com")
                .name("John")
                .build();
        setField(testUser1, "id", 1L);

        testUser2 = User.builder()
                .authUserId("auth-2")
                .email("user2@example.com")
                .name("Jane")
                .build();
        setField(testUser2, "id", 2L);

        testWorkspace = Workspace.builder()
                .name("Test Workspace")
                .build();
        setField(testWorkspace, "id", 1L);

        testChannel = Channel.builder()
                .workspace(testWorkspace)
                .name("general")
                .type(ChannelType.PUBLIC)
                .createdBy(1L)
                .build();
        setField(testChannel, "id", 100L);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("read receipt를 업데이트할 수 있다")
    void updateReadReceipt_Success() {
        // given
        lenient().doNothing().when(valueOperations).set(anyString(), anyString());

        // when
        readReceiptService.updateReadReceipt(USER_ID, CHANNEL_ID, TIMESTAMP_ID);

        // then
        String expectedKey = "read_receipt:1:100";
        verify(valueOperations, times(1)).set(eq(expectedKey), eq(TIMESTAMP_ID));
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/channel.100"),
                any(com.slack.dto.websocket.WebSocketMessage.class)
        );
    }

    @Test
    @DisplayName("유효하지 않은 timestamp는 무시된다")
    void updateReadReceipt_InvalidTimestamp() {
        // when
        readReceiptService.updateReadReceipt(USER_ID, CHANNEL_ID, null);
        readReceiptService.updateReadReceipt(USER_ID, CHANNEL_ID, "");

        // then
        verify(valueOperations, never()).set(anyString(), anyString());
        verify(messagingTemplate, never()).convertAndSend(
                anyString(),
                any(com.slack.dto.websocket.WebSocketMessage.class)
        );
    }

    @Test
    @DisplayName("Redis에서 read receipt를 조회할 수 있다")
    void getReadReceipt_Success() {
        // given
        String key = "read_receipt:1:100";
        when(valueOperations.get(key)).thenReturn(TIMESTAMP_ID);

        // when
        String result = readReceiptService.getReadReceipt(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).isEqualTo(TIMESTAMP_ID);
        verify(valueOperations, times(1)).get(key);
    }

    @Test
    @DisplayName("read receipt가 없으면 null을 반환한다")
    void getReadReceipt_NotFound() {
        // given
        String key = "read_receipt:1:100";
        when(valueOperations.get(key)).thenReturn(null);

        // when
        String result = readReceiptService.getReadReceipt(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("잘못된 형식의 read receipt도 문자열로 반환한다")
    void getReadReceipt_ReturnsString() {
        // given
        String key = "read_receipt:1:100";
        String anyTimestamp = "any-timestamp-value";
        when(valueOperations.get(key)).thenReturn(anyTimestamp);

        // when
        String result = readReceiptService.getReadReceipt(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).isEqualTo(anyTimestamp);
    }

    @Test
    @DisplayName("채널의 모든 read receipt를 조회할 수 있다")
    void getChannelReadReceipts_Success() {
        // given
        List<Long> memberIds = Arrays.asList(1L, 2L);
        when(channelMemberRepository.findUserIdsByChannelId(CHANNEL_ID)).thenReturn(memberIds);
        when(valueOperations.get("read_receipt:1:100")).thenReturn("1735046400000050");
        when(valueOperations.get("read_receipt:2:100")).thenReturn("1735046400000045");

        // when
        Map<Long, String> readReceipts = readReceiptService.getChannelReadReceipts(CHANNEL_ID, USER_ID);

        // then
        assertThat(readReceipts).hasSize(2);
        assertThat(readReceipts.get(1L)).isEqualTo("1735046400000050");
        assertThat(readReceipts.get(2L)).isEqualTo("1735046400000045");
    }

    @Test
    @DisplayName("read receipt가 없는 멤버는 결과에 포함되지 않는다")
    void getChannelReadReceipts_SomeMembersNoReceipt() {
        // given
        List<Long> memberIds = Arrays.asList(1L, 2L, 3L);
        when(channelMemberRepository.findUserIdsByChannelId(CHANNEL_ID)).thenReturn(memberIds);
        when(valueOperations.get("read_receipt:1:100")).thenReturn("1735046400000050");
        when(valueOperations.get("read_receipt:2:100")).thenReturn(null);
        when(valueOperations.get("read_receipt:3:100")).thenReturn("1735046400000030");

        // when
        Map<Long, String> readReceipts = readReceiptService.getChannelReadReceipts(CHANNEL_ID, USER_ID);

        // then
        assertThat(readReceipts).hasSize(2);
        assertThat(readReceipts.get(1L)).isEqualTo("1735046400000050");
        assertThat(readReceipts.get(3L)).isEqualTo("1735046400000030");
        assertThat(readReceipts).doesNotContainKey(2L);
    }

    @Test
    @DisplayName("read receipt 업데이트 시 채널 멤버에게 브로드캐스트된다")
    void updateReadReceipt_BroadcastsToChannelMembers() {
        // given
        lenient().doNothing().when(valueOperations).set(anyString(), anyString());

        // when
        readReceiptService.updateReadReceipt(USER_ID, CHANNEL_ID, TIMESTAMP_ID);

        // then
        ArgumentCaptor<com.slack.dto.websocket.WebSocketMessage> messageCaptor =
                ArgumentCaptor.forClass(com.slack.dto.websocket.WebSocketMessage.class);
        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/channel.100"),
                messageCaptor.capture()
        );

        com.slack.dto.websocket.WebSocketMessage message = messageCaptor.getValue();
        assertThat(message.getType()).isEqualTo(com.slack.dto.websocket.WebSocketMessage.MessageType.READ);
        assertThat(message.getChannelId()).isEqualTo(CHANNEL_ID);
        assertThat(message.getUserId()).isEqualTo(USER_ID);
        assertThat(message.getCreatedAt()).isEqualTo(TIMESTAMP_ID);
    }
}
