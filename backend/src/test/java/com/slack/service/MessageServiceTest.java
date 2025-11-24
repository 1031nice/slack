package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.message.Message;
import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.dto.message.MessageCreateRequest;
import com.slack.dto.message.MessageResponse;
import com.slack.repository.ChannelRepository;
import com.slack.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService 단위 테스트")
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private MessageService messageService;

    private User testUser;
    private Workspace testWorkspace;
    private Channel testChannel;
    private Message testMessage;

    @BeforeEach
    void setUp() throws Exception {
        testUser = User.builder()
                .authUserId("auth-123")
                .email("test@example.com")
                .name("Test User")
                .build();
        setField(testUser, "id", 1L);

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
        setField(testChannel, "id", 1L);

        testMessage = Message.builder()
                .channel(testChannel)
                .user(testUser)
                .content("Hello, World!")
                .parentMessage(null)
                .build();
        setField(testMessage, "id", 1L);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("메시지를 생성할 수 있다")
    void createMessage_Success() {
        // given
        MessageCreateRequest request = MessageCreateRequest.builder()
                .userId(1L)
                .content("Hello, World!")
                .build();

        when(channelRepository.findById(1L)).thenReturn(Optional.of(testChannel));
        when(userService.findById(1L)).thenReturn(testUser);
        when(messageRepository.save(any(Message.class))).thenReturn(testMessage);

        // when
        MessageResponse result = messageService.createMessage(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hello, World!");
        assertThat(result.getChannelId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(1L);
        verify(channelRepository, times(1)).findById(1L);
        verify(userService, times(1)).findById(1L);
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    @DisplayName("존재하지 않는 채널에 메시지를 생성하면 예외가 발생한다")
    void createMessage_ChannelNotFound() {
        // given
        MessageCreateRequest request = MessageCreateRequest.builder()
                .userId(1L)
                .content("Hello, World!")
                .build();

        when(channelRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> messageService.createMessage(999L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Channel not found with id: 999");
    }

    @Test
    @DisplayName("ID로 메시지를 조회할 수 있다")
    void getMessageById_Success() {
        // given
        when(messageRepository.findById(1L)).thenReturn(Optional.of(testMessage));

        // when
        MessageResponse result = messageService.getMessageById(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getContent()).isEqualTo("Hello, World!");
        verify(messageRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 메시지를 조회하면 예외가 발생한다")
    void getMessageById_NotFound() {
        // given
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> messageService.getMessageById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Message not found with id: 999");
    }

    @Test
    @DisplayName("채널의 메시지 목록을 조회할 수 있다 - beforeId 없음")
    void getChannelMessages_WithoutBeforeId() throws Exception {
        // given
        Message message1 = Message.builder()
                .channel(testChannel)
                .user(testUser)
                .content("Message 1")
                .build();
        setField(message1, "id", 1L);

        Message message2 = Message.builder()
                .channel(testChannel)
                .user(testUser)
                .content("Message 2")
                .build();
        setField(message2, "id", 2L);

        when(messageRepository.findMessagesByChannelId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(Arrays.asList(message1, message2));

        // when
        List<MessageResponse> result = messageService.getChannelMessages(1L, 50, null);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(MessageResponse::getContent)
                .containsExactly("Message 1", "Message 2");
        verify(messageRepository, times(1)).findMessagesByChannelId(eq(1L), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("채널의 메시지 목록을 조회할 수 있다 - beforeId 있음")
    void getChannelMessages_WithBeforeId() throws Exception {
        // given
        Message message1 = Message.builder()
                .channel(testChannel)
                .user(testUser)
                .content("Message 1")
                .build();
        setField(message1, "id", 1L);

        when(messageRepository.findMessagesByChannelId(eq(1L), eq(10L), any(Pageable.class)))
                .thenReturn(Arrays.asList(message1));

        // when
        List<MessageResponse> result = messageService.getChannelMessages(1L, 50, 10L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        verify(messageRepository, times(1)).findMessagesByChannelId(eq(1L), eq(10L), any(Pageable.class));
    }

    @Test
    @DisplayName("limit 파라미터가 최대값을 초과하면 100으로 제한된다")
    void getChannelMessages_LimitExceedsMax() {
        // given
        when(messageRepository.findMessagesByChannelId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(Arrays.asList());

        // when
        messageService.getChannelMessages(1L, 200, null);

        // then
        verify(messageRepository, times(1)).findMessagesByChannelId(
                eq(1L),
                isNull(),
                argThat(pageable -> pageable.getPageSize() == 100)
        );
    }
}

