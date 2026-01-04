package com.slack.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Channel routing service using consistent hashing ring.
 *
 * Maps channels to specific servers using consistent hashing to ensure
 * that all messages for a given channel are handled by the same server.
 * This guarantees perfect message ordering within each channel.
 *
 * Uses virtual nodes (replicas) to improve load distribution when
 * servers are added or removed from the cluster.
 */
@Service
public class ChannelRoutingService {

    private final int serverId;
    private final int totalServers;
    private final SortedMap<Long, Integer> ring;
    private static final int VIRTUAL_NODES = 150; // Number of virtual nodes per server

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

        this.ring = buildConsistentHashRing(totalServers);
    }

    /**
     * Build consistent hashing ring with virtual nodes.
     *
     * Each server is assigned multiple virtual nodes distributed around the ring
     * to ensure even load distribution when servers are added/removed.
     *
     * @param totalServers number of servers in the cluster
     * @return sorted map of hash values to server IDs
     */
    private SortedMap<Long, Integer> buildConsistentHashRing(int totalServers) {
        SortedMap<Long, Integer> ring = new TreeMap<>();

        for (int server = 0; server < totalServers; server++) {
            for (int replica = 0; replica < VIRTUAL_NODES; replica++) {
                String key = "server-" + server + "-replica-" + replica;
                long hash = hash(key);
                ring.put(hash, server);
            }
        }

        return ring;
    }

    /**
     * Determine which server should handle this channel using consistent hashing.
     *
     * Finds the first virtual node on the ring at or after the channel's hash value.
     * If no node is found after the hash, wraps around to the first node.
     *
     * @param channelId the channel ID
     * @return server ID (0-based index)
     */
    public int getServerForChannel(Long channelId) {
        if (ring.isEmpty()) {
            return 0;
        }

        long hash = hash("channel-" + channelId);

        // Find first node >= hash
        SortedMap<Long, Integer> tailMap = ring.tailMap(hash);
        Long key = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();

        return ring.get(key);
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
     * Hash function using MD5.
     *
     * Produces uniform distribution across the hash ring.
     *
     * @param key the key to hash
     * @return 64-bit hash value
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));

            // Use first 8 bytes as long (big-endian)
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            // MD5 is always available in JVM
            throw new RuntimeException("MD5 algorithm not found", e);
        }
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
