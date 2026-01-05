package com.slack.workspace.mapper;

import com.slack.workspace.domain.Workspace;
import com.slack.workspace.dto.WorkspaceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WorkspaceMapper {

    @Mapping(source = "owner.id", target = "ownerId")
    WorkspaceResponse toResponse(Workspace workspace);
}
