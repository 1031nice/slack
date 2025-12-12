package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.mention.Mention;
import com.slack.domain.message.Message;
import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.repository.MentionRepository;
import com.slack.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MentionService 단위 테스트")
class MentionServiceTest {

    @Mock
    private MentionRepository mentionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MentionService mentionService;

    private User testUser1;
    private User testUser2;
    private User testUser3;
    private Workspace testWorkspace;
    private Channel testChannel;
    private Message testMessage;
    private Mention testMention;

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

        testUser3 = User.builder()
                .authUserId("auth-3")
                .email("user3@example.com")
                .name("Bob")
                .build();
        setField(testUser3, "id", 3L);

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

        testMessage = Message.builder()
                .channel(testChannel)
                .user(testUser1)
                .content("Hello @Jane and @Bob!")
                .parentMessage(null)
                .sequenceNumber(1L)
                .build();
        setField(testMessage, "id", 1000L);
        setField(testMessage, "createdAt", LocalDateTime.now());

        testMention = Mention.builder()
                .message(testMessage)
                .mentionedUser(testUser2)
                .isRead(false)
                .build();
        setField(testMention, "id", 5000L);
        setField(testMention, "createdAt", LocalDateTime.now());
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("@username을 파싱할 수 있다")
    void parseMentions_Success() {
        // given
        String content = "Hello @John and @Jane! How are you @Bob?";

        // when
        Set<String> mentions = mentionService.parseMentions(content);

        // then
        assertThat(mentions).hasSize(3);
        assertThat(mentions).containsExactlyInAnyOrder("John", "Jane", "Bob");
    }

    @Test
    @DisplayName("중복된 @username은 한 번만 파싱된다")
    void parseMentions_Duplicate() {
        // given
        String content = "Hello @John, @John, and @John again!";

        // when
        Set<String> mentions = mentionService.parseMentions(content);

        // then
        assertThat(mentions).hasSize(1);
        assertThat(mentions).containsExactly("John");
    }

    @Test
    @DisplayName("언더스코어와 하이픈이 포함된 username을 파싱할 수 있다")
    void parseMentions_WithUnderscoreAndHyphen() {
        // given
        String content = "Hello @john_doe and @user-name!";

        // when
        Set<String> mentions = mentionService.parseMentions(content);

        // then
        assertThat(mentions).hasSize(2);
        assertThat(mentions).containsExactlyInAnyOrder("john_doe", "user-name");
    }

    @Test
    @DisplayName("숫자가 포함된 username을 파싱할 수 있다")
    void parseMentions_WithNumbers() {
        // given
        String content = "Hello @user123 and @test456!";

        // when
        Set<String> mentions = mentionService.parseMentions(content);

        // then
        assertThat(mentions).hasSize(2);
        assertThat(mentions).containsExactlyInAnyOrder("user123", "test456");
    }

    @Test
    @DisplayName("@가 없으면 mention이 파싱되지 않는다")
    void parseMentions_NoAtSymbol() {
        // given
        String content = "Hello John and Jane!";

        // when
        Set<String> mentions = mentionService.parseMentions(content);

        // then
        assertThat(mentions).isEmpty();
    }

    @Test
    @DisplayName("빈 문자열이나 null은 빈 Set을 반환한다")
    void parseMentions_EmptyOrNull() {
        // when & then
        assertThat(mentionService.parseMentions("")).isEmpty();
        assertThat(mentionService.parseMentions(null)).isEmpty();
    }

    @Test
    @DisplayName("메시지에서 mention을 생성할 수 있다")
    void createMentions_Success() throws Exception {
        // given
        String content = "Hello @Jane and @Bob!";
        testMessage = Message.builder()
                .channel(testChannel)
                .user(testUser1)
                .content(content)
                .parentMessage(null)
                .sequenceNumber(1L)
                .build();
        setField(testMessage, "id", 1000L);
        setField(testMessage, "createdAt", LocalDateTime.now());

        when(userRepository.findByNameIgnoreCase("Jane")).thenReturn(Arrays.asList(testUser2));
        when(userRepository.findByNameIgnoreCase("Bob")).thenReturn(Arrays.asList(testUser3));
        when(mentionRepository.findByMessageIdAndMentionedUserId(1000L, 2L))
                .thenReturn(Optional.empty());
        when(mentionRepository.findByMessageIdAndMentionedUserId(1000L, 3L))
                .thenReturn(Optional.empty());
        when(mentionRepository.save(any(Mention.class))).thenAnswer(invocation -> {
            Mention mention = invocation.getArgument(0);
            setField(mention, "id", 5000L);
            setField(mention, "createdAt", LocalDateTime.now());
            return mention;
        });

        // when
        List<Mention> mentions = mentionService.createMentions(testMessage);

        // then
        assertThat(mentions).hasSize(2);
        
        ArgumentCaptor<Mention> mentionCaptor = ArgumentCaptor.forClass(Mention.class);
        verify(mentionRepository, times(2)).save(mentionCaptor.capture());
        
        List<Mention> savedMentions = mentionCaptor.getAllValues();
        assertThat(savedMentions).extracting(m -> m.getMentionedUser().getId())
                .containsExactlyInAnyOrder(2L, 3L);
        
        // Verify WebSocket notifications were sent
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(com.slack.dto.websocket.WebSocketMessage.class));
    }

    @Test
    @DisplayName("자기 자신을 mention하면 생성되지 않는다")
    void createMentions_SelfMention() throws Exception {
        // given
        String content = "Hello @John!";
        testMessage = Message.builder()
                .channel(testChannel)
                .user(testUser1)
                .content(content)
                .parentMessage(null)
                .sequenceNumber(1L)
                .build();
        setField(testMessage, "id", 1000L);
        setField(testMessage, "createdAt", LocalDateTime.now());

        when(userRepository.findByNameIgnoreCase("John")).thenReturn(Arrays.asList(testUser1));

        // when
        List<Mention> mentions = mentionService.createMentions(testMessage);

        // then
        assertThat(mentions).isEmpty();
        verify(mentionRepository, never()).save(any(Mention.class));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(com.slack.dto.websocket.WebSocketMessage.class));
    }

    @Test
    @DisplayName("존재하지 않는 username은 mention이 생성되지 않는다")
    void createMentions_UserNotFound() throws Exception {
        // given
        String content = "Hello @NonExistentUser!";
        testMessage = Message.builder()
                .channel(testChannel)
                .user(testUser1)
                .content(content)
                .parentMessage(null)
                .sequenceNumber(1L)
                .build();
        setField(testMessage, "id", 1000L);
        setField(testMessage, "createdAt", LocalDateTime.now());

        when(userRepository.findByNameIgnoreCase("NonExistentUser")).thenReturn(Arrays.asList());

        // when
        List<Mention> mentions = mentionService.createMentions(testMessage);

        // then
        assertThat(mentions).isEmpty();
        verify(mentionRepository, never()).save(any(Mention.class));
    }

    @Test
    @DisplayName("이미 존재하는 mention은 중복 생성되지 않는다")
    void createMentions_DuplicateMention() throws Exception {
        // given
        String content = "Hello @Jane!";
        testMessage = Message.builder()
                .channel(testChannel)
                .user(testUser1)
                .content(content)
                .parentMessage(null)
                .sequenceNumber(1L)
                .build();
        setField(testMessage, "id", 1000L);
        setField(testMessage, "createdAt", LocalDateTime.now());

        when(userRepository.findByNameIgnoreCase("Jane")).thenReturn(Arrays.asList(testUser2));
        when(mentionRepository.findByMessageIdAndMentionedUserId(1000L, 2L))
                .thenReturn(Optional.of(testMention));

        // when
        List<Mention> mentions = mentionService.createMentions(testMessage);

        // then
        assertThat(mentions).isEmpty();
        verify(mentionRepository, never()).save(any(Mention.class));
    }

    @Test
    @DisplayName("사용자의 모든 mention을 조회할 수 있다")
    void getMentionsByUserId_Success() {
        // given
        Mention mention1 = Mention.builder()
                .message(testMessage)
                .mentionedUser(testUser2)
                .isRead(false)
                .build();
        Mention mention2 = Mention.builder()
                .message(testMessage)
                .mentionedUser(testUser2)
                .isRead(true)
                .build();

        when(mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(2L))
                .thenReturn(Arrays.asList(mention1, mention2));

        // when
        List<Mention> mentions = mentionService.getMentionsByUserId(2L);

        // then
        assertThat(mentions).hasSize(2);
        verify(mentionRepository, times(1)).findByMentionedUserIdOrderByCreatedAtDesc(2L);
    }

    @Test
    @DisplayName("사용자의 읽지 않은 mention만 조회할 수 있다")
    void getUnreadMentionsByUserId_Success() {
        // given
        Mention unreadMention = Mention.builder()
                .message(testMessage)
                .mentionedUser(testUser2)
                .isRead(false)
                .build();

        when(mentionRepository.findUnreadMentionsByUserId(2L))
                .thenReturn(Arrays.asList(unreadMention));

        // when
        List<Mention> mentions = mentionService.getUnreadMentionsByUserId(2L);

        // then
        assertThat(mentions).hasSize(1);
        assertThat(mentions.get(0).getIsRead()).isFalse();
        verify(mentionRepository, times(1)).findUnreadMentionsByUserId(2L);
    }

    @Test
    @DisplayName("mention을 읽음 처리할 수 있다")
    void markMentionAsRead_Success() {
        // given
        when(mentionRepository.findById(5000L)).thenReturn(Optional.of(testMention));
        when(mentionRepository.save(any(Mention.class))).thenReturn(testMention);

        // when
        mentionService.markMentionAsRead(5000L);

        // then
        verify(mentionRepository, times(1)).findById(5000L);
        verify(mentionRepository, times(1)).save(testMention);
        assertThat(testMention.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 mention을 읽음 처리하면 예외가 발생한다")
    void markMentionAsRead_NotFound() {
        // given
        when(mentionRepository.findById(9999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> mentionService.markMentionAsRead(9999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Mention not found with id: 9999");
    }

    @Test
    @DisplayName("대소문자 구분 없이 username을 매칭할 수 있다")
    void createMentions_CaseInsensitive() throws Exception {
        // given
        String content = "Hello @JANE and @bob!";
        testMessage = Message.builder()
                .channel(testChannel)
                .user(testUser1)
                .content(content)
                .parentMessage(null)
                .sequenceNumber(1L)
                .build();
        setField(testMessage, "id", 1000L);
        setField(testMessage, "createdAt", LocalDateTime.now());

        when(userRepository.findByNameIgnoreCase("JANE")).thenReturn(Arrays.asList(testUser2));
        when(userRepository.findByNameIgnoreCase("bob")).thenReturn(Arrays.asList(testUser3));
        when(mentionRepository.findByMessageIdAndMentionedUserId(1000L, 2L))
                .thenReturn(Optional.empty());
        when(mentionRepository.findByMessageIdAndMentionedUserId(1000L, 3L))
                .thenReturn(Optional.empty());
        when(mentionRepository.save(any(Mention.class))).thenAnswer(invocation -> {
            Mention mention = invocation.getArgument(0);
            setField(mention, "id", 5000L);
            setField(mention, "createdAt", LocalDateTime.now());
            return mention;
        });

        // when
        List<Mention> mentions = mentionService.createMentions(testMessage);

        // then
        assertThat(mentions).hasSize(2);
        verify(userRepository, times(1)).findByNameIgnoreCase("JANE");
        verify(userRepository, times(1)).findByNameIgnoreCase("bob");
    }
}
