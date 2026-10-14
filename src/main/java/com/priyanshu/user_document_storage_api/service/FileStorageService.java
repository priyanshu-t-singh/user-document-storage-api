package com.priyanshu.user_document_storage_api.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.priyanshu.user_document_storage_api.dto.FileMetadata;
import com.priyanshu.user_document_storage_api.dto.PaginatedResponse;

public interface FileStorageService {

    PaginatedResponse<FileMetadata> searchFiles(String userName, String searchTerm, int pageSize,
            String continuationToken);

    Resource downloadFile(String userName, String fileName);

    String getPresignedDownloadUrl(String userName, String fileName);

    void uploadFile(String userName, MultipartFile file);

}
