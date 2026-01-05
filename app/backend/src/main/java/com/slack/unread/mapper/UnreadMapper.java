package com.slack.unread.mapper;

import com.slack.message.domain.Message;
import com.slack.unread.dto.UnreadMessageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UnreadMapper {

    @Mapping(target = "messageId", source = "message.id")
    @Mapping(target = "userId", source = "message.user.id")
    @Mapping(target = "content", source = "message.content")
    @Mapping(target = "createdAt", source = "message.createdAt")
    @Mapping(target = "timestampId", source = "message.timestampId")
    @Mapping(target = "channelId", source = "channelId")
    @Mapping(target = "channelName", source = "channelName")
    UnreadMessageResponse toUnreadMessageResponse(Message message, Long channelId, String channelName);
}
