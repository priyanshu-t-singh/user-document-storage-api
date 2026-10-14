package com.priyanshu.user_document_storage_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.priyanshu.user_document_storage_api.config.StorageProperties;
import com.priyanshu.user_document_storage_api.dto.FileMetadata;
import com.priyanshu.user_document_storage_api.dto.PaginatedResponse;
import com.priyanshu.user_document_storage_api.exception.StorageException;
import com.priyanshu.user_document_storage_api.service.FileStorageService;
import com.priyanshu.user_document_storage_api.service.search.FileSearchStrategy;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final StorageProperties storageProperties;
    private final FileSearchStrategy searchStrategy;

    private static final String USER_PREFIX_FORMAT = "users/%s/";

    @Override
    public PaginatedResponse<FileMetadata> searchFiles(
            String userName,
            String searchTerm,
            int pageSize,
            String continuationToken) {

        String prefix = String.format(USER_PREFIX_FORMAT, userName);
        List<FileMetadata> matchedFiles = new ArrayList<>();
        String currentToken = continuationToken;
        boolean isTruncated;

        try {
            do {
                ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                        .bucket(storageProperties.getBucketName())
                        .prefix(prefix)
                        .maxKeys(pageSize); // S3 batch size

                if (currentToken != null && !currentToken.isBlank()) {
                    requestBuilder.continuationToken(currentToken);
                }

                ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

                for (S3Object s3Object : response.contents()) {
                    String fullKey = s3Object.key();
                    // Extract relative filename by stripping the "users/{userName}/" prefix
                    String fileName = fullKey.substring(prefix.length());

                    // Ignore directory entries and empty filenames
                    if (fileName.isEmpty() || fileName.endsWith("/")) {
                        continue;
                    }

                    if (searchStrategy.matches(fileName, searchTerm)) {
                        matchedFiles.add(FileMetadata.builder()
                                .fileName(fileName)
                                .sizeBytes(s3Object.size())
                                .lastModified(s3Object.lastModified())
                                .build());

                        if (matchedFiles.size() == pageSize) {
                            break;
                        }
                    }
                }

                isTruncated = response.isTruncated();
                currentToken = response.nextContinuationToken();

            } while (matchedFiles.size() < pageSize && isTruncated);

            return new PaginatedResponse<>(matchedFiles, isTruncated ? currentToken : null);

        } catch (SdkException e) {
            log.error("Failed to list S3 objects for user [{}]: {}", userName, e.getMessage(), e);
            throw new StorageException("Error accessing file storage", e);
        }
    }

    @Override
    public Resource downloadFile(String userName, String fileName) {
        // TODO: Implement download functionality
        throw new UnsupportedOperationException("Download functionality will be implemented");
    }

    @Override
    public void uploadFile(String userName, MultipartFile file) {
        // TODO: Implement upload functionality
        throw new UnsupportedOperationException("Upload functionality will be implemented");
    }

    @Override
    public String getPresignedDownloadUrl(String userName, String fileName) {
        // TODO Implement presigned URL generation functionality
        throw new UnsupportedOperationException("Presigned URL generation functionality will be implemented");
    }

}
