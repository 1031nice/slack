package com.slack.unread.dto;

/**
 * Sort options for unread messages view.
 */
public enum UnreadSortOption {
    /**
     * Sort by creation time, newest first
     */
    NEWEST,

    /**
     * Sort by creation time, oldest first
     */
    OLDEST,

    /**
     * Sort by channel name, then by creation time (newest first) within each channel
     */
    CHANNEL;

    /**
     * Parse sort option from string with default fallback.
     *
     * @param sort Sort string (case-insensitive)
     * @return UnreadSortOption, defaults to NEWEST if invalid
     */
    public static UnreadSortOption fromString(String sort) {
        if (sort == null || sort.isEmpty()) {
            return NEWEST;
        }

        try {
            return UnreadSortOption.valueOf(sort.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NEWEST;
        }
    }
}
