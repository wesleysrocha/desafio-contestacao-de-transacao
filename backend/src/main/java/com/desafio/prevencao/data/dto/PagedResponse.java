package com.desafio.prevencao.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
public class PagedResponse<T> {

    private List<T> content;

    private Pagination pagination;

    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                new Pagination(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages())
        );
    }

    @Getter
    @AllArgsConstructor
    public static class Pagination {

        @JsonProperty("pageNumber")
        private int pageNumber;

        @JsonProperty("pageSize")
        private int pageSize;

        @JsonProperty("totalElements")
        private long totalElements;

        @JsonProperty("totalPages")
        private int totalPages;
    }
}
