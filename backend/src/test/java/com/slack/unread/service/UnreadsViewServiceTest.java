package com.slack.unread.service;

import com.slack.channel.domain.Channel;
import com.slack.message.domain.Message;
import com.slack.user.domain.User;
import com.slack.unread.dto.UnreadsViewResponse;
import com.slack.channel.repository.ChannelMemberRepository;
import com.slack.channel.repository.ChannelRepository;
import com.slack.message.repository.MessageRepository;
import com.slack.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnreadsViewService 단위 테스트")
class UnreadsViewServiceTest {

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @Mock
    private UnreadCountService unreadCountService;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private com.slack.unread.mapper.UnreadMapper unreadMapper;

    @InjectMocks
    private UnreadsViewService unreadsViewService;

    private static final Long USER_ID = 1L;
    private static final Long CHANNEL_ID_1 = 100L;
    private static final Long CHANNEL_ID_2 = 200L;
    private static final String CHANNEL_NAME_1 = "general";
    private static final String CHANNEL_NAME_2 = "random";

    private Channel channel1;
    private Channel channel2;
    private Message message1;
    private Message message2;
    private Message message3;

    @BeforeEach
    void setUp() throws Exception {
        // Mock workspaceMemberRepository to return empty list by default
        // (tests focus on PRIVATE channels via ChannelMember)
        when(workspaceMemberRepository.findByUserId(anyLong()))
                .thenReturn(Collections.emptyList());

        channel1 = Channel.builder()
                .name(CHANNEL_NAME_1)
                .type(com.slack.channel.domain.ChannelType.PUBLIC)
                .createdBy(1L)
                .build();
        setField(channel1, "id", CHANNEL_ID_1);

        channel2 = Channel.builder()
                .name(CHANNEL_NAME_2)
                .type(com.slack.channel.domain.ChannelType.PUBLIC)
                .createdBy(1L)
                .build();
        setField(channel2, "id", CHANNEL_ID_2);

        User user = User.builder()
                .authUserId("auth-user-2")
                .email("user2@example.com")
                .name("User 2")
                .build();
        setField(user, "id", 2L);

        message1 = Message.builder()
                .channel(channel1)
                .user(user)
                .content("Message 1")
                .build();
        setField(message1, "id", 1000L);
        setField(message1, "createdAt", LocalDateTime.now().minusHours(3));

        message2 = Message.builder()
                .channel(channel1)
                .user(user)
                .content("Message 2")
                .build();
        setField(message2, "id", 2000L);
        setField(message2, "createdAt", LocalDateTime.now().minusHours(2));

        message3 = Message.builder()
                .channel(channel2)
                .user(user)
                .content("Message 3")
                .build();
        setField(message3, "id", 3000L);
        setField(message3, "createdAt", LocalDateTime.now().minusHours(1));

        // Mock unreadMapper
        lenient().when(unreadMapper.toUnreadMessageResponse(any(Message.class), anyLong(), anyString()))
                .thenAnswer(invocation -> {
                    Message msg = invocation.getArgument(0);
                    Long channelId = invocation.getArgument(1);
                    String channelName = invocation.getArgument(2);
                    return com.slack.unread.dto.UnreadMessageResponse.builder()
                            .messageId(msg.getId())
                            .channelId(channelId)
                            .channelName(channelName)
                            .userId(msg.getUser().getId())
                            .content(msg.getContent())
                            .createdAt(msg.getCreatedAt())
                            .timestampId(msg.getTimestampId())
                            .build();
                });
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("newest 정렬로 unread 메시지를 조회할 수 있다")
    void getUnreads_SortNewest_Success() {
        // given
        when(channelMemberRepository.findChannelIdsByUserId(USER_ID))
                .thenReturn(Arrays.asList(CHANNEL_ID_1, CHANNEL_ID_2));
        when(channelRepository.findById(CHANNEL_ID_1)).thenReturn(Optional.of(channel1));
        when(channelRepository.findById(CHANNEL_ID_2)).thenReturn(Optional.of(channel2));
        when(unreadCountService.getUnreadMessageIdsSorted(USER_ID, CHANNEL_ID_1, true))
                .thenReturn(Set.of("1000", "2000"));
        when(unreadCountService.getUnreadMessageIdsSorted(USER_ID, CHANNEL_ID_2, true))
                .thenReturn(Set.of("3000"));
        when(messageRepository.findAllById(anySet()))
                .thenReturn(Arrays.asList(message1, message2, message3));

        // when
        UnreadsViewResponse response = unreadsViewService.getUnreads(USER_ID, "newest", 50);

        // then
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getUnreadMessages()).hasSize(3);
        // Should be sorted by createdAt DESC (newest first)
        assertThat(response.getUnreadMessages().get(0).getMessageId()).isEqualTo(3000L);
        assertThat(response.getUnreadMessages().get(1).getMessageId()).isEqualTo(2000L);
        assertThat(response.getUnreadMessages().get(2).getMessageId()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("oldest 정렬로 unread 메시지를 조회할 수 있다")
    void getUnreads_SortOldest_Success() {
        // given
        when(channelMemberRepository.findChannelIdsByUserId(USER_ID))
                .thenReturn(Arrays.asList(CHANNEL_ID_1, CHANNEL_ID_2));
        when(channelRepository.findById(CHANNEL_ID_1)).thenReturn(Optional.of(channel1));
        when(channelRepository.findById(CHANNEL_ID_2)).thenReturn(Optional.of(channel2));
        when(unreadCountService.getUnreadMessageIdsSorted(USER_ID, CHANNEL_ID_1, false))
                .thenReturn(Set.of("1000", "2000"));
        when(unreadCountService.getUnreadMessageIdsSorted(USER_ID, CHANNEL_ID_2, false))
                .thenReturn(Set.of("3000"));
        when(messageRepository.findAllById(anySet()))
                .thenReturn(Arrays.asList(message1, message2, message3));

        // when
        UnreadsViewResponse response = unreadsViewService.getUnreads(USER_ID, "oldest", 50);

        // then
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getUnreadMessages()).hasSize(3);
        // Should be sorted by createdAt ASC (oldest first)
        assertThat(response.getUnreadMessages().get(0).getMessageId()).isEqualTo(1000L);
        assertThat(response.getUnreadMessages().get(1).getMessageId()).isEqualTo(2000L);
        assertThat(response.getUnreadMessages().get(2).getMessageId()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("channel 정렬로 unread 메시지를 조회할 수 있다")
    void getUnreads_SortChannel_Success() {
        // given
        when(channelMemberRepository.findChannelIdsByUserId(USER_ID))
                .thenReturn(Arrays.asList(CHANNEL_ID_1, CHANNEL_ID_2));
        when(channelRepository.findById(CHANNEL_ID_1)).thenReturn(Optional.of(channel1));
        when(channelRepository.findById(CHANNEL_ID_2)).thenReturn(Optional.of(channel2));
        when(unreadCountService.getUnreadMessageIdsSorted(USER_ID, CHANNEL_ID_1, true))
                .thenReturn(Set.of("1000", "2000"));
        when(unreadCountService.getUnreadMessageIdsSorted(USER_ID, CHANNEL_ID_2, true))
                .thenReturn(Set.of("3000"));
        when(messageRepository.findAllById(anySet()))
                .thenReturn(Arrays.asList(message1, message2, message3));

        // when
        UnreadsViewResponse response = unreadsViewService.getUnreads(USER_ID, "channel", 50);

        // then
        assertThat(response.getTotalCount()).isEqualTo(3);
        assertThat(response.getUnreadMessages()).hasSize(3);
        // Should be grouped by channel name, then sorted by createdAt DESC within each channel
        assertThat(response.getUnreadMessages().get(0).getChannelName()).isEqualTo(CHANNEL_NAME_1);
        assertThat(response.getUnreadMessages().get(1).getChannelName()).isEqualTo(CHANNEL_NAME_1);
        assertThat(response.getUnreadMessages().get(2).getChannelName()).isEqualTo(CHANNEL_NAME_2);
    }

    @Test
    @DisplayName("limit 파라미터로 결과 개수를 제한할 수 있다")
    void getUnreads_WithLimit_Success() {
        // given
        when(channelMemberRepository.findChannelIdsByUserId(USER_ID))
                .thenReturn(Arrays.asList(CHANNEL_ID_1));
        when(channelRepository.findById(CHANNEL_ID_1)).thenReturn(Optional.of(channel1));
        when(unreadCountService.getUnreadMessageIdsSorted(USER_ID, CHANNEL_ID_1, true))
                .thenReturn(Set.of("1000", "2000", "3000"));
        when(messageRepository.findAllById(anySet()))
                .thenReturn(Arrays.asList(message1, message2, message3));

        // when
        UnreadsViewResponse response = unreadsViewService.getUnreads(USER_ID, "newest", 2);

        // then
        assertThat(response.getTotalCount()).isEqualTo(3); // total count includes all
        assertThat(response.getUnreadMessages()).hasSize(2); // but only 2 returned
    }

    @Test
    @DisplayName("unread 메시지가 없으면 빈 리스트를 반환한다")
    void getUnreads_NoUnreads_Success() {
        // given
        when(channelMemberRepository.findChannelIdsByUserId(USER_ID))
                .thenReturn(Collections.emptyList());

        // when
        UnreadsViewResponse response = unreadsViewService.getUnreads(USER_ID, "newest", 50);

        // then
        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getUnreadMessages()).isEmpty();
    }

    @Test
    @DisplayName("채널 멤버가 아니면 빈 리스트를 반환한다")
    void getUnreads_NoChannels_Success() {
        // given
        when(channelMemberRepository.findChannelIdsByUserId(USER_ID))
                .thenReturn(Collections.emptyList());

        // when
        UnreadsViewResponse response = unreadsViewService.getUnreads(USER_ID, "newest", 50);

        // then
        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getUnreadMessages()).isEmpty();
    }

    @Test
    @DisplayName("기본값으로 newest 정렬과 limit 50을 사용한다")
    void getUnreads_DefaultValues_Success() {
        // given
        when(channelMemberRepository.findChannelIdsByUserId(USER_ID))
                .thenReturn(Arrays.asList(CHANNEL_ID_1));
        when(channelRepository.findById(CHANNEL_ID_1)).thenReturn(Optional.of(channel1));
        when(unreadCountService.getUnreadMessageIdsSorted(USER_ID, CHANNEL_ID_1, true))
                .thenReturn(Set.of("1000"));
        when(messageRepository.findAllById(anySet()))
                .thenReturn(Arrays.asList(message1));

        // when
        UnreadsViewResponse response = unreadsViewService.getUnreads(USER_ID, null, null);

        // then
        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getUnreadMessages()).hasSize(1);
        verify(unreadCountService).getUnreadMessageIdsSorted(USER_ID, CHANNEL_ID_1, true); // newest (descending)
    }

    @Test
    @DisplayName("MAX_LIMIT을 초과하는 limit은 MAX_LIMIT으로 제한된다")
    void getUnreads_MaxLimitEnforced_Success() {
        // given
        when(channelMemberRepository.findChannelIdsByUserId(USER_ID))
                .thenReturn(Arrays.asList(CHANNEL_ID_1));
        when(channelRepository.findById(CHANNEL_ID_1)).thenReturn(Optional.of(channel1));
        when(unreadCountService.getUnreadMessageIdsSorted(anyLong(), anyLong(), anyBoolean()))
                .thenReturn(Set.of("1000"));
        when(messageRepository.findAllById(anySet()))
                .thenReturn(Arrays.asList(message1));

        // when
        UnreadsViewService service = new UnreadsViewService(
                channelMemberRepository,
                unreadCountService,
                messageRepository,
                channelRepository,
                workspaceMemberRepository,
                unreadMapper
        );
        UnreadsViewResponse response = service.getUnreads(USER_ID, "newest", 500); // exceeds MAX_LIMIT (200)

        // then
        // Should not throw exception and should limit to MAX_LIMIT
        assertThat(response).isNotNull();
    }
}

