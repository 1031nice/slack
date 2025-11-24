package com.slack.service;

import com.slack.domain.user.User;
import com.slack.domain.workspace.Workspace;
import com.slack.dto.workspace.WorkspaceCreateRequest;
import com.slack.dto.workspace.WorkspaceResponse;
import com.slack.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceService 단위 테스트")
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private WorkspaceService workspaceService;

    private User testUser;
    private Workspace testWorkspace;

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
                .owner(testUser)
                .build();
        setField(testWorkspace, "id", 1L);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("워크스페이스를 생성할 수 있다")
    void createWorkspace_Success() {
        // given
        WorkspaceCreateRequest request = WorkspaceCreateRequest.builder()
                .name("New Workspace")
                .ownerId(1L)
                .build();

        when(userService.findByAuthUserId("auth-123")).thenReturn(testUser);
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(testWorkspace);

        // when
        WorkspaceResponse result = workspaceService.createWorkspace(request, "auth-123");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Workspace");
        assertThat(result.getOwnerId()).isEqualTo(1L);
        verify(userService, times(1)).findByAuthUserId("auth-123");
        verify(workspaceRepository, times(1)).save(any(Workspace.class));
    }

    @Test
    @DisplayName("ID로 워크스페이스를 조회할 수 있다")
    void getWorkspaceById_Success() {
        // given
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(testWorkspace));

        // when
        WorkspaceResponse result = workspaceService.getWorkspaceById(1L);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Workspace");
        verify(workspaceRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스를 조회하면 예외가 발생한다")
    void getWorkspaceById_NotFound() {
        // given
        when(workspaceRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> workspaceService.getWorkspaceById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Workspace not found with id: 999");
    }

    @Test
    @DisplayName("사용자의 워크스페이스 목록을 조회할 수 있다")
    void getUserWorkspaces_Success() throws Exception {
        // given
        User otherUser = User.builder()
                .authUserId("auth-456")
                .email("other@example.com")
                .name("Other User")
                .build();
        setField(otherUser, "id", 2L);

        Workspace workspace1 = Workspace.builder()
                .name("Workspace 1")
                .owner(testUser)
                .build();
        setField(workspace1, "id", 1L);

        Workspace workspace2 = Workspace.builder()
                .name("Workspace 2")
                .owner(testUser)
                .build();
        setField(workspace2, "id", 2L);

        Workspace otherWorkspace = Workspace.builder()
                .name("Other Workspace")
                .owner(otherUser)
                .build();
        setField(otherWorkspace, "id", 3L);

        when(userService.findByAuthUserId("auth-123")).thenReturn(testUser);
        when(workspaceRepository.findAll()).thenReturn(Arrays.asList(workspace1, workspace2, otherWorkspace));

        // when
        List<WorkspaceResponse> result = workspaceService.getUserWorkspaces("auth-123");

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(WorkspaceResponse::getName)
                .containsExactlyInAnyOrder("Workspace 1", "Workspace 2");
        verify(userService, times(1)).findByAuthUserId("auth-123");
        verify(workspaceRepository, times(1)).findAll();
    }
}

