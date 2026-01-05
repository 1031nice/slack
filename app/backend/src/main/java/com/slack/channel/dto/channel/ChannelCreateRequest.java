package com.slack.channel.dto;

import com.slack.channel.domain.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelCreateRequest {

    @NotBlank(message = "Channel name is required")
    @Size(min = 1, max = 255, message = "Channel name must be between 1 and 255 characters")
    private String name;

    @NotNull(message = "Channel type is required")
    private ChannelType type;
}

