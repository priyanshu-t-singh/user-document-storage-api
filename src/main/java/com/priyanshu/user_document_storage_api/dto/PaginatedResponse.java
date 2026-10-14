package com.priyanshu.user_document_storage_api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class PaginatedResponse<T> {
    private List<T> items;
    private int pageNumber;
    private int pageSize;
    private int totalItems;
}
