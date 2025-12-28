package com.slack.channel.mapper;

import com.slack.channel.domain.Channel;
import com.slack.channel.dto.ChannelResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import org.mapstruct.Builder;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = false))
public interface ChannelMapper {

    @Mapping(source = "workspace.id", target = "workspaceId")
    @Mapping(target = "unreadCount", ignore = true)
    ChannelResponse toResponse(Channel channel);
}
