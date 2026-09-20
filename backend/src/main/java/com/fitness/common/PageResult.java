package com.fitness.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private final List<T> items;
    private final long total;
    private final int page;
    private final int size;
    private final boolean hasMore;

    public PageResult(List<T> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
        this.hasMore = (long) (page + 1) * size < total;
    }
}
