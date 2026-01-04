package com.slack.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Channel routing service for consistent hashing.
 *
 * Maps channels to specific servers using consistent hashing to ensure
 * that all messages for a given channel are handled by the same server.
 * This guarantees perfect message ordering within each channel.
 *
 * Approach based on Slack's production architecture:
 * "Channel Servers are stateful and in-memory, holding channel history,
 * with every CS mapped to a subset of channels based on consistent hashing"
 *
 * @see <a href="https://www.infoq.com/news/2023/04/real-time-messaging-slack/">
 *      Real-Time Messaging Architecture at Slack</a>
 */
@Service
public class ChannelRoutingService {

    private final int serverId;
    private final int totalServers;

    public ChannelRoutingService(
            @Value("${server.id:0}") int serverId,
            @Value("${cluster.total-servers:1}") int totalServers) {
        this.serverId = serverId;
        this.totalServers = totalServers;

        if (serverId < 0 || serverId >= totalServers) {
            throw new IllegalArgumentException(
                String.format("Invalid server.id=%d. Must be between 0 and %d",
                    serverId, totalServers - 1));
        }
    }

    /**
     * Determine which server should handle this channel.
     *
     * Uses simple modulo hashing. Production Slack uses consistent hashing ring
     * for better rebalancing when adding/removing servers.
     *
     * @param channelId the channel ID
     * @return server ID (0-based index)
     */
    public int getServerForChannel(Long channelId) {
        return Math.abs(channelId.hashCode()) % totalServers;
    }

    /**
     * Check if THIS server is responsible for handling this channel.
     *
     * @param channelId the channel ID
     * @return true if this server should handle the channel
     */
    public boolean isResponsibleFor(Long channelId) {
        return getServerForChannel(channelId) == serverId;
    }

    /**
     * Get this server's ID.
     */
    public int getServerId() {
        return serverId;
    }

    /**
     * Get total number of servers in the cluster.
     */
    public int getTotalServers() {
        return totalServers;
    }
}
