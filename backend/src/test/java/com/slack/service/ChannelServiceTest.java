package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.workspace.Workspace;
import com.slack.dto.channel.ChannelCreateRequest;
import com.slack.dto.channel.ChannelResponse;
import com.slack.repository.ChannelRepository;
import com.slack.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelService 단위 테스트")
class ChannelServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private UnreadCountService unreadCountService;

    @InjectMocks
    private ChannelService channelService;

    private Workspace testWorkspace;
    private Channel testChannel;

    @BeforeEach
    void setUp() throws Exception {
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
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("채널을 생성할 수 있다")
    void createChannel_Success() {
        // given
        ChannelCreateRequest request = ChannelCreateRequest.builder()
                .name("random")
                .type(ChannelType.PUBLIC)
                .createdBy(1L)
                .build();

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));
        when(channelRepository.save(any(Channel.class))).thenReturn(testChannel);

        // when
        ChannelResponse result = channelService.createChannel(1L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("general");
        assertThat(result.getType()).isEqualTo(ChannelType.PUBLIC);
        verify(workspaceRepository, times(1)).findById(1L);
        verify(channelRepository, times(1)).save(any(Channel.class));
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스에 채널을 생성하면 예외가 발생한다")
    void createChannel_WorkspaceNotFound() {
        // given
        ChannelCreateRequest request = ChannelCreateRequest.builder()
                .name("random")
                .type(ChannelType.PUBLIC)
                .createdBy(1L)
                .build();

        when(workspaceRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> channelService.createChannel(999L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Workspace not found with id: 999");
    }

    @Test
    @DisplayName("ID로 채널을 조회할 수 있다")
    void getChannelById_Success() {
        // given
        when(channelRepository.findById(1L)).thenReturn(Optional.of(testChannel));

        // when
        ChannelResponse result = channelService.getChannelById(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("general");
        verify(channelRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 채널을 조회하면 예외가 발생한다")
    void getChannelById_NotFound() {
        // given
        when(channelRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> channelService.getChannelById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Channel not found with id: 999");
    }

    @Test
    @DisplayName("워크스페이스의 채널 목록을 조회할 수 있다")
    void getWorkspaceChannels_Success() throws Exception {
        // given
        Long userId = 1L;
        Channel channel1 = Channel.builder()
                .workspace(testWorkspace)
                .name("general")
                .type(ChannelType.PUBLIC)
                .createdBy(1L)
                .build();
        setField(channel1, "id", 1L);
        setField(channel1, "createdAt", java.time.LocalDateTime.now());
        setField(channel1, "updatedAt", java.time.LocalDateTime.now());

        Channel channel2 = Channel.builder()
                .workspace(testWorkspace)
                .name("random")
                .type(ChannelType.PUBLIC)
                .createdBy(1L)
                .build();
        setField(channel2, "id", 2L);
        setField(channel2, "createdAt", java.time.LocalDateTime.now());
        setField(channel2, "updatedAt", java.time.LocalDateTime.now());

        when(channelRepository.findByWorkspaceId(1L)).thenReturn(Arrays.asList(channel1, channel2));
        when(permissionService.isWorkspaceMember(userId, 1L)).thenReturn(true);
        when(unreadCountService.getUnreadCount(anyLong(), anyLong())).thenReturn(0L);

        // when
        List<ChannelResponse> result = channelService.getWorkspaceChannels(1L, userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ChannelResponse::getName)
                .containsExactlyInAnyOrder("general", "random");
        verify(channelRepository, times(1)).findByWorkspaceId(1L);
        verify(permissionService, times(2)).isWorkspaceMember(userId, 1L);
    }
}

