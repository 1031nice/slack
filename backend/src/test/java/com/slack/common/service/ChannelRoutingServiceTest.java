package com.slack.common.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelRoutingServiceTest {

    @Test
    void shouldRouteSameChannelToSameServer() {
        // Given: 3-server cluster, this is server 1
        ChannelRoutingService service = new ChannelRoutingService(1, 3);
        Long channelId = 123L;

        // When: Check routing multiple times
        int server1 = service.getServerForChannel(channelId);
        int server2 = service.getServerForChannel(channelId);

        // Then: Always same server
        assertThat(server1).isEqualTo(server2);
    }

    @Test
    void shouldDistributeChannelsAcrossServers() {
        // Given: 3-server cluster
        ChannelRoutingService service = new ChannelRoutingService(0, 3);

        // When: Check many channels
        int[] serverCounts = new int[3];
        for (long channelId = 1; channelId <= 300; channelId++) {
            int serverId = service.getServerForChannel(channelId);
            serverCounts[serverId]++;
        }

        // Then: Roughly even distribution (within 20%)
        // Each server should handle ~100 channels
        for (int count : serverCounts) {
            assertThat(count).isBetween(80, 120);
        }
    }

    @Test
    void shouldValidateResponsibility() {
        // Given: 3-server cluster, this is server 0
        ChannelRoutingService service = new ChannelRoutingService(0, 3);

        // When: Find channels that map to each server
        Long channelForServer0 = null;
        Long channelForServer1 = null;
        Long channelForServer2 = null;

        for (long i = 1; i <= 100; i++) {
            int server = service.getServerForChannel(i);
            if (server == 0 && channelForServer0 == null) channelForServer0 = i;
            if (server == 1 && channelForServer1 == null) channelForServer1 = i;
            if (server == 2 && channelForServer2 == null) channelForServer2 = i;

            if (channelForServer0 != null && channelForServer1 != null && channelForServer2 != null) {
                break;
            }
        }

        // Then: Validate responsibility
        assertThat(service.getServerForChannel(channelForServer0)).isEqualTo(0);
        assertThat(service.isResponsibleFor(channelForServer0)).isTrue();

        assertThat(service.getServerForChannel(channelForServer1)).isEqualTo(1);
        assertThat(service.isResponsibleFor(channelForServer1)).isFalse();

        assertThat(service.getServerForChannel(channelForServer2)).isEqualTo(2);
        assertThat(service.isResponsibleFor(channelForServer2)).isFalse();
    }

    @Test
    void shouldRejectInvalidServerId() {
        // When/Then: Invalid server IDs
        assertThatThrownBy(() -> new ChannelRoutingService(-1, 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid server.id=-1");

        assertThatThrownBy(() -> new ChannelRoutingService(3, 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid server.id=3");

        assertThatThrownBy(() -> new ChannelRoutingService(10, 3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid server.id=10");
    }

    @Test
    void shouldHandleSingleServerCluster() {
        // Given: Single server cluster
        ChannelRoutingService service = new ChannelRoutingService(0, 1);

        // When/Then: All channels go to server 0
        for (long channelId = 1; channelId <= 100; channelId++) {
            assertThat(service.getServerForChannel(channelId)).isEqualTo(0);
            assertThat(service.isResponsibleFor(channelId)).isTrue();
        }
    }

    @Test
    void shouldProvideClusterInfo() {
        // Given
        ChannelRoutingService service = new ChannelRoutingService(1, 3);

        // When/Then
        assertThat(service.getServerId()).isEqualTo(1);
        assertThat(service.getTotalServers()).isEqualTo(3);
    }
}
