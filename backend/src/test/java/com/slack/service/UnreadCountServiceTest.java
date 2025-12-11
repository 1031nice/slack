package com.slack.service;

import com.slack.repository.ChannelMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnreadCountService 단위 테스트")
class UnreadCountServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks
    private UnreadCountService unreadCountService;

    private static final Long USER_ID = 1L;
    private static final Long CHANNEL_ID = 100L;
    private static final Long MESSAGE_ID = 1000L;
    private static final Long SENDER_ID = 1L;
    private static final long TIMESTAMP = 1234567890L;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("unread count를 조회할 수 있다")
    void getUnreadCount_Success() {
        // given
        String key = "unread:" + USER_ID + ":" + CHANNEL_ID;
        when(zSetOperations.count(eq(key), anyDouble(), anyDouble())).thenReturn(5L);

        // when
        long result = unreadCountService.getUnreadCount(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).isEqualTo(5L);
        verify(zSetOperations, times(1)).count(eq(key), eq(Double.NEGATIVE_INFINITY), eq(Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("unread count가 없으면 0을 반환한다")
    void getUnreadCount_NoUnread() {
        // given
        String key = "unread:" + USER_ID + ":" + CHANNEL_ID;
        when(zSetOperations.count(eq(key), anyDouble(), anyDouble())).thenReturn(0L);

        // when
        long result = unreadCountService.getUnreadCount(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("unread count가 null이면 0을 반환한다")
    void getUnreadCount_NullCount() {
        // given
        String key = "unread:" + USER_ID + ":" + CHANNEL_ID;
        when(zSetOperations.count(eq(key), anyDouble(), anyDouble())).thenReturn(null);

        // when
        long result = unreadCountService.getUnreadCount(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("메시지 생성 시 모든 채널 멤버의 unread count를 증가시킨다 (sender 제외)")
    void incrementUnreadCount_Success() {
        // given
        Long member1Id = 2L;
        Long member2Id = 3L;
        Long member3Id = 4L;
        List<Long> memberIds = Arrays.asList(SENDER_ID, member1Id, member2Id, member3Id);

        when(channelMemberRepository.findUserIdsByChannelId(CHANNEL_ID)).thenReturn(memberIds);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        // when
        unreadCountService.incrementUnreadCount(CHANNEL_ID, MESSAGE_ID, SENDER_ID, TIMESTAMP);

        // then
        // sender를 제외한 3명의 멤버에 대해 unread count가 증가해야 함
        verify(channelMemberRepository, times(1)).findUserIdsByChannelId(CHANNEL_ID);
        
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> timestampCaptor = ArgumentCaptor.forClass(Double.class);
        
        verify(zSetOperations, times(3)).add(
                keyCaptor.capture(),
                messageIdCaptor.capture(),
                timestampCaptor.capture()
        );

        List<String> keys = keyCaptor.getAllValues();
        assertThat(keys).hasSize(3);
        assertThat(keys).containsExactlyInAnyOrder(
                "unread:2:100",
                "unread:3:100",
                "unread:4:100"
        );
        assertThat(keys).doesNotContain("unread:1:100"); // sender는 제외

        List<String> messageIds = messageIdCaptor.getAllValues();
        assertThat(messageIds).hasSize(3);
        assertThat(messageIds).allMatch(id -> id.equals("1000"));

        List<Double> timestamps = timestampCaptor.getAllValues();
        assertThat(timestamps).hasSize(3);
        assertThat(timestamps).allMatch(ts -> ts == TIMESTAMP);
    }

    @Test
    @DisplayName("sender만 있는 경우 unread count가 증가하지 않는다")
    void incrementUnreadCount_OnlySender() {
        // given
        List<Long> memberIds = Arrays.asList(SENDER_ID);

        when(channelMemberRepository.findUserIdsByChannelId(CHANNEL_ID)).thenReturn(memberIds);

        // when
        unreadCountService.incrementUnreadCount(CHANNEL_ID, MESSAGE_ID, SENDER_ID, TIMESTAMP);

        // then
        verify(channelMemberRepository, times(1)).findUserIdsByChannelId(CHANNEL_ID);
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("채널 멤버가 없는 경우 unread count가 증가하지 않는다")
    void incrementUnreadCount_NoMembers() {
        // given
        List<Long> memberIds = Arrays.asList();

        when(channelMemberRepository.findUserIdsByChannelId(CHANNEL_ID)).thenReturn(memberIds);

        // when
        unreadCountService.incrementUnreadCount(CHANNEL_ID, MESSAGE_ID, SENDER_ID, TIMESTAMP);

        // then
        verify(channelMemberRepository, times(1)).findUserIdsByChannelId(CHANNEL_ID);
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("unread count를 초기화할 수 있다")
    void clearUnreadCount_Success() {
        // given
        String key = "unread:" + USER_ID + ":" + CHANNEL_ID;
        lenient().when(redisTemplate.delete(key)).thenReturn(true);

        // when
        unreadCountService.clearUnreadCount(USER_ID, CHANNEL_ID);

        // then
        verify(redisTemplate, times(1)).delete(key);
    }

    @Test
    @DisplayName("unread message IDs를 조회할 수 있다")
    void getUnreadMessageIds_Success() {
        // given
        String key = "unread:" + USER_ID + ":" + CHANNEL_ID;
        Set<Object> messageIds = new HashSet<>(Arrays.asList("1000", "1001", "1002"));
        when(zSetOperations.range(key, 0, -1)).thenReturn(messageIds);

        // when
        Set<Object> result = unreadCountService.getUnreadMessageIds(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).hasSize(3);
        assertThat(result).containsExactlyInAnyOrder("1000", "1001", "1002");
        verify(zSetOperations, times(1)).range(key, 0, -1);
    }

    @Test
    @DisplayName("unread message가 없으면 빈 Set을 반환한다")
    void getUnreadMessageIds_NoUnread() {
        // given
        String key = "unread:" + USER_ID + ":" + CHANNEL_ID;
        when(zSetOperations.range(key, 0, -1)).thenReturn(new HashSet<>());

        // when
        Set<Object> result = unreadCountService.getUnreadMessageIds(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).isEmpty();
        verify(zSetOperations, times(1)).range(key, 0, -1);
    }
}
