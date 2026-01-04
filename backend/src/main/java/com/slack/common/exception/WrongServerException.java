package com.slack.common.exception;

/**
 * Exception thrown when a message is routed to the wrong server.
 *
 * In channel partitioning architecture, each channel should be handled by
 * exactly one server (determined by consistent hashing). If a message arrives
 * at the wrong server, this exception is thrown to alert the client to reconnect.
 */
public class WrongServerException extends RuntimeException {

    private final Long channelId;
    private final int expectedServerId;
    private final int actualServerId;

    public WrongServerException(Long channelId, int expectedServerId, int actualServerId) {
        super(String.format(
            "Channel %d should be handled by server %d, but received by server %d",
            channelId, expectedServerId, actualServerId
        ));
        this.channelId = channelId;
        this.expectedServerId = expectedServerId;
        this.actualServerId = actualServerId;
    }

    public Long getChannelId() {
        return channelId;
    }

    public int getExpectedServerId() {
        return expectedServerId;
    }

    public int getActualServerId() {
        return actualServerId;
    }
}
