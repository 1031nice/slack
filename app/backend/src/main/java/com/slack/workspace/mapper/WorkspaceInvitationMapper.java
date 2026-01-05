package com.slack.workspace.mapper;

import com.slack.workspace.domain.WorkspaceInvitation;
import com.slack.workspace.dto.WorkspaceInviteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkspaceInvitationMapper {

    @Mapping(source = "workspace.id", target = "workspaceId")
    @Mapping(target = "status", expression = "java(invitation.getStatus().name())")
    WorkspaceInviteResponse toResponse(WorkspaceInvitation invitation);
}
