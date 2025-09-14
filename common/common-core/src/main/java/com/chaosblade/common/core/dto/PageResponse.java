package com.chaosblade.common.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 分页响应类
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {
    
    private List<T> items;
    private long total;
    private int page;
    private int size;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    
    public PageResponse() {}
    
    public PageResponse(List<T> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = (int) Math.ceil((double) total / size);
        this.hasNext = page < totalPages;
        this.hasPrevious = page > 1;
    }
    
    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        return new PageResponse<>(items, total, page, size);
    }
    
    public List<T> getItems() {
        return items;
    }
    
    public void setItems(List<T> items) {
        this.items = items;
    }
    
    public long getTotal() {
        return total;
    }
    
    public void setTotal(long total) {
        this.total = total;
    }
    
    public int getPage() {
        return page;
    }
    
    public void setPage(int page) {
        this.page = page;
    }
    
    public int getSize() {
        return size;
    }
    
    public void setSize(int size) {
        this.size = size;
    }
    
    public int getTotalPages() {
        return totalPages;
    }
    
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
    
    public boolean isHasNext() {
        return hasNext;
    }
    
    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }
    
    public boolean isHasPrevious() {
        return hasPrevious;
    }
    
    public void setHasPrevious(boolean hasPrevious) {
        this.hasPrevious = hasPrevious;
    }
}
