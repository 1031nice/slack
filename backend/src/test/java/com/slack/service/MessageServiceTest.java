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
@DisplayName("MessageService Unit Tests")
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private UserService userService;

    @Mock
    private UnreadCountService unreadCountService;

    @Mock
    private MentionService mentionService;

    @Mock
    private PermissionService permissionService;

    @Mock
    private MessageTimestampGenerator timestampGenerator;

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
                .timestampId("1640995200123.000001")
                .build();
        setField(testMessage, "id", 1L);
        setField(testMessage, "createdAt", java.time.LocalDateTime.now());
        setField(testMessage, "updatedAt", java.time.LocalDateTime.now());
        setField(testMessage, "sequenceNumber", 1L);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Should create message successfully")
    void createMessage_Success() {
        // given
        MessageCreateRequest request = MessageCreateRequest.builder()
                .userId(1L)
                .content("Hello, World!")
                .build();

        when(channelRepository.findById(1L)).thenReturn(Optional.of(testChannel));
        when(userService.findById(1L)).thenReturn(testUser);
        when(timestampGenerator.generateTimestampId()).thenReturn("1640995200123.000001");
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
    @DisplayName("Should throw exception when channel not found")
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
    @DisplayName("Should get message by ID successfully")
    void getMessageById_Success() {
        // given
        when(messageRepository.findById(1L)).thenReturn(Optional.of(testMessage));

        // when
        MessageResponse result = messageService.getMessageById(1L, testUser.getId());

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getContent()).isEqualTo("Hello, World!");
        verify(messageRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should throw exception when message not found")
    void getMessageById_NotFound() {
        // given
        when(messageRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> messageService.getMessageById(999L, testUser.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Message not found with id: 999");
    }

    @Test
    @DisplayName("Should get channel messages without beforeId")
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
        List<MessageResponse> result = messageService.getChannelMessages(1L, testUser.getId(), 50, null);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(MessageResponse::getContent)
                .containsExactly("Message 1", "Message 2");
        verify(messageRepository, times(1)).findMessagesByChannelId(eq(1L), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("Should get channel messages with beforeId")
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
        List<MessageResponse> result = messageService.getChannelMessages(1L, testUser.getId(), 50, 10L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        verify(messageRepository, times(1)).findMessagesByChannelId(eq(1L), eq(10L), any(Pageable.class));
    }

    @Test
    @DisplayName("Should limit page size to max when limit exceeds maximum")
    void getChannelMessages_LimitExceedsMax() {
        // given
        when(messageRepository.findMessagesByChannelId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(Arrays.asList());

        // when
        messageService.getChannelMessages(1L, testUser.getId(), 200, null);

        // then
        verify(messageRepository, times(1)).findMessagesByChannelId(
                eq(1L),
                isNull(),
                argThat(pageable -> pageable.getPageSize() == 100)
        );
    }

    @Test
    @DisplayName("Should get messages after sequence number successfully")
    void getMessagesAfterSequence_Success() throws Exception {
        // given
        Message message1 = Message.builder()
                .channel(testChannel)
                .user(testUser)
                .content("Message 1")
                .build();
        setField(message1, "id", 1L);
        setField(message1, "sequenceNumber", 5L);

        Message message2 = Message.builder()
                .channel(testChannel)
                .user(testUser)
                .content("Message 2")
                .build();
        setField(message2, "id", 2L);
        setField(message2, "sequenceNumber", 6L);

        when(messageRepository.findMessagesAfterSequence(1L, 3L))
                .thenReturn(Arrays.asList(message1, message2));

        // when
        List<MessageResponse> result = messageService.getMessagesAfterSequence(1L, 3L);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(MessageResponse::getSequenceNumber)
                .containsExactly(5L, 6L);
        verify(messageRepository, times(1)).findMessagesAfterSequence(1L, 3L);
    }

    @Test
    @DisplayName("Should return empty list when no messages after sequence")
    void getMessagesAfterSequence_NoMessages() {
        // given
        when(messageRepository.findMessagesAfterSequence(1L, 999L))
                .thenReturn(Arrays.asList());

        // when
        List<MessageResponse> result = messageService.getMessagesAfterSequence(1L, 999L);

        // then
        assertThat(result).isEmpty();
        verify(messageRepository, times(1)).findMessagesAfterSequence(1L, 999L);
    }

    @Test
    @DisplayName("Should throw exception when channelId is null for getMessagesAfterSequence")
    void getMessagesAfterSequence_NullChannelId() {
        // when & then
        assertThatThrownBy(() -> messageService.getMessagesAfterSequence(null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid channel ID");

        verify(messageRepository, never()).findMessagesAfterSequence(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should throw exception when channelId is negative for getMessagesAfterSequence")
    void getMessagesAfterSequence_NegativeChannelId() {
        // when & then
        assertThatThrownBy(() -> messageService.getMessagesAfterSequence(-1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid channel ID");

        verify(messageRepository, never()).findMessagesAfterSequence(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should throw exception when sequence number is null")
    void getMessagesAfterSequence_NullSequenceNumber() {
        // when & then
        assertThatThrownBy(() -> messageService.getMessagesAfterSequence(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sequence number");

        verify(messageRepository, never()).findMessagesAfterSequence(anyLong(), any());
    }

    @Test
    @DisplayName("Should throw exception when sequence number is negative")
    void getMessagesAfterSequence_NegativeSequenceNumber() {
        // when & then
        assertThatThrownBy(() -> messageService.getMessagesAfterSequence(1L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sequence number");

        verify(messageRepository, never()).findMessagesAfterSequence(anyLong(), anyLong());
    }

    @Test
    @DisplayName("Should throw exception when channelId is invalid for getMessageById")
    void getMessageById_InvalidChannelId() {
        // when & then
        assertThatThrownBy(() -> messageService.getMessageById(null, testUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid message ID");

        assertThatThrownBy(() -> messageService.getMessageById(0L, testUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid message ID");

        assertThatThrownBy(() -> messageService.getMessageById(-1L, testUser.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid message ID");

        verify(messageRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Should throw exception when channelId is invalid for getChannelMessages")
    void getChannelMessages_InvalidChannelId() {
        // when & then
        assertThatThrownBy(() -> messageService.getChannelMessages(null, testUser.getId(), 50, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid channel ID");

        assertThatThrownBy(() -> messageService.getChannelMessages(0L, testUser.getId(), 50, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid channel ID");

        assertThatThrownBy(() -> messageService.getChannelMessages(-1L, testUser.getId(), 50, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid channel ID");

        verify(messageRepository, never()).findMessagesByChannelId(anyLong(), any(), any());
    }
}

