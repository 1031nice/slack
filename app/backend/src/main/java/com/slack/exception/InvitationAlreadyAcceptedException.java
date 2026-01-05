package com.slack.exception;

public class InvitationAlreadyAcceptedException extends RuntimeException {
    public InvitationAlreadyAcceptedException(String message) {
        super(message);
    }
}

