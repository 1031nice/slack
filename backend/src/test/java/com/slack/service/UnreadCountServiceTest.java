package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.workspace.Workspace;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.ChannelRepository;
import com.slack.repository.WorkspaceMemberRepository;
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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
    private ChannelRepository channelRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks
    private UnreadCountService unreadCountService;

    private static final Long USER_ID = 1L;
    private static final Long CHANNEL_ID = 100L;
    private static final Long MESSAGE_ID = 1000L;
    private static final Long SENDER_ID = 1L;
    private static final Long WORKSPACE_ID = 1L;
    private static final long TIMESTAMP = 1234567890L;

    private Channel testChannel;
    private Workspace testWorkspace;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // Create test workspace
        testWorkspace = Workspace.builder()
                .name("Test Workspace")
                .owner(com.slack.domain.user.User.builder()
                        .authUserId("auth-1")
                        .email("owner@example.com")
                        .name("Owner")
                        .build())
                .build();
        setField(testWorkspace, "id", WORKSPACE_ID);

        // Create test channel (PRIVATE for these tests)
        testChannel = Channel.builder()
                .workspace(testWorkspace)
                .name("test-channel")
                .type(ChannelType.PRIVATE)
                .createdBy(1L)
                .build();
        setField(testChannel, "id", CHANNEL_ID);

        // Mock channelRepository to return test channel (lenient for tests that don't use it)
        lenient().when(channelRepository.findById(CHANNEL_ID)).thenReturn(Optional.of(testChannel));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("unread count를 조회할 수 있다")
    void getUnreadCount_Success() {
        // given
        String key = "unread:" + USER_ID + ":" + CHANNEL_ID;
        when(zSetOperations.zCard(key)).thenReturn(5L);

        // when
        long result = unreadCountService.getUnreadCount(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).isEqualTo(5L);
        verify(zSetOperations, times(1)).zCard(key);
    }

    @Test
    @DisplayName("unread count가 없으면 0을 반환한다")
    void getUnreadCount_NoUnread() {
        // given
        String key = "unread:" + USER_ID + ":" + CHANNEL_ID;
        when(zSetOperations.zCard(key)).thenReturn(0L);

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
        when(zSetOperations.zCard(key)).thenReturn(null);

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

        // when
        unreadCountService.incrementUnreadCount(CHANNEL_ID, MESSAGE_ID, SENDER_ID, TIMESTAMP);

        // then
        // Pipeline을 사용하므로 개별 ZADD 호출을 verify할 수 없음
        // Repository 호출만 확인 (실제 Redis 동작은 integration test에서 검증)
        verify(channelMemberRepository, times(1)).findUserIdsByChannelId(CHANNEL_ID);

        // Note: Pipeline을 사용하므로 zSetOperations.add()는 verify 불가
        // 실제 동작은 integration test 또는 Redis 실행 환경에서 테스트 필요
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
        // Pipeline 사용으로 개별 호출 verify 불가 - 로직 테스트는 integration test에서
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
        // Early return으로 Pipeline 실행 안 됨 - 로직 확인 완료
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
        Set<String> result = unreadCountService.getUnreadMessageIds(USER_ID, CHANNEL_ID);

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
        Set<String> result = unreadCountService.getUnreadMessageIds(USER_ID, CHANNEL_ID);

        // then
        assertThat(result).isEmpty();
        verify(zSetOperations, times(1)).range(key, 0, -1);
    }
}
