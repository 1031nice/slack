package com.slack.controller;

import com.slack.dto.message.MessageCreateRequest;
import com.slack.dto.message.MessageResponse;
import com.slack.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/channels/{channelId}/messages")
    public ResponseEntity<MessageResponse> createMessage(
            @PathVariable Long channelId,
            @Valid @RequestBody MessageCreateRequest request) {
        MessageResponse response = messageService.createMessage(channelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/channels/{channelId}/messages")
    public ResponseEntity<List<MessageResponse>> getChannelMessages(
            @PathVariable Long channelId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long before) {
        List<MessageResponse> messages = messageService.getChannelMessages(channelId, limit, before);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/messages/{messageId}")
    public ResponseEntity<MessageResponse> getMessageById(@PathVariable Long messageId) {
        MessageResponse response = messageService.getMessageById(messageId);
        return ResponseEntity.ok(response);
    }
}

