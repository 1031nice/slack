package com.slack.unread.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadsViewResponse {
    private List<UnreadMessageResponse> unreadMessages;
    private int totalCount;
}

