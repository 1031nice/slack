package com.slack.workspace.domain;

import com.slack.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Workspace Domain Tests")
class WorkspaceTest {

    private User owner;
    private User member;
    private Workspace workspace;

    @BeforeEach
    void setUp() throws Exception {
        owner = User.builder()
                .authUserId("owner-auth-id")
                .email("owner@example.com")
                .name("Owner User")
                .build();
        setField(owner, "id", 1L);

        member = User.builder()
                .authUserId("member-auth-id")
                .email("member@example.com")
                .name("Member User")
                .build();
        setField(member, "id", 2L);

        workspace = Workspace.builder()
                .name("Test Workspace")
                .owner(owner)
                .build();
        setField(workspace, "id", 100L);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Should return OWNER role when userId matches workspace owner")
    void getRoleForUser_OwnerUser_ReturnsOwnerRole() {
        WorkspaceRole role = workspace.getRoleForUser(owner.getId());

        assertThat(role).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("Should return MEMBER role when userId does not match workspace owner")
    void getRoleForUser_NonOwnerUser_ReturnsMemberRole() {
        WorkspaceRole role = workspace.getRoleForUser(member.getId());

        assertThat(role).isEqualTo(WorkspaceRole.MEMBER);
    }

    @Test
    @DisplayName("Should return MEMBER role for completely different userId")
    void getRoleForUser_DifferentUserId_ReturnsMemberRole() {
        Long differentUserId = 999L;

        WorkspaceRole role = workspace.getRoleForUser(differentUserId);

        assertThat(role).isEqualTo(WorkspaceRole.MEMBER);
    }
}
