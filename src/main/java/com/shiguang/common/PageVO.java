package com.shiguang.common;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PageVO<T> {

    private List<T> items;

    private String nextCursor;

    private Boolean hasMore;
}