package com.sentinelhub.common.dto;

import java.util.List;

/**
 * Paginated list response wrapper.
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int pageSize
) {
}
