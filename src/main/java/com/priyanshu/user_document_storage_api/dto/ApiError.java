package com.priyanshu.user_document_storage_api.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ApiError {
    private String error;
    private String message;
    private int status;
    private Instant timestamp;
}
