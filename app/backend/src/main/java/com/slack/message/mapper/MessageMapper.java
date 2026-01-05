package com.slack.message.mapper;

import com.slack.message.domain.Message;
import com.slack.message.dto.MessageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    @Mapping(source = "channel.id", target = "channelId")
    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "parentMessage.id", target = "parentMessageId")
    MessageResponse toResponse(Message message);
}
