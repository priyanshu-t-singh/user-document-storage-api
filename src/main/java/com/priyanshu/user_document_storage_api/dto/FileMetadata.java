package com.priyanshu.user_document_storage_api.dto;

import java.time.Instant;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FileMetadata {
    private String fileName;
    private long sizeBytes;
    private Instant lastModified;
}
