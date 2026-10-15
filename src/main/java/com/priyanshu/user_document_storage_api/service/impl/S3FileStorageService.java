package com.priyanshu.user_document_storage_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.priyanshu.user_document_storage_api.config.CacheConfig;
import com.priyanshu.user_document_storage_api.config.StorageProperties;
import com.priyanshu.user_document_storage_api.dto.FileMetadata;
import com.priyanshu.user_document_storage_api.dto.PaginatedResponse;
import com.priyanshu.user_document_storage_api.exception.StorageException;
import com.priyanshu.user_document_storage_api.service.FileStorageService;
import com.priyanshu.user_document_storage_api.service.search.FileSearchStrategy;
import com.priyanshu.user_document_storage_api.exception.FileNotFoundException;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties storageProperties;
    private final FileSearchStrategy searchStrategy;

    @Override
    @Cacheable(
            value = CacheConfig.FILE_LIST_CACHE,
            key = "#userName + ':' + #searchTerm + ':' + #pageSize",
            condition = "#continuationToken == null || #continuationToken.isBlank()")
    public PaginatedResponse<FileMetadata> searchFiles(
            String userName,
            String searchTerm,
            int pageSize,
            String continuationToken) {

        String prefix = storageProperties.getKeyPrefix();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        prefix = String.format(prefix + "/%s/", userName);

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
        String key = getS3Key(userName, fileName);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();

            ResponseInputStream<GetObjectResponse> s3ObjectStream = s3Client.getObject(getObjectRequest);
            return new InputStreamResource(s3ObjectStream);

        } catch (NoSuchKeyException e) {
            log.error("File not found in S3: {}", key);
            throw new FileNotFoundException("File not found: " + fileName);
        } catch (SdkException e) {
            log.error("Error downloading file from S3: {}", key, e);
            throw new StorageException("Error downloading file", e);
        }
    }

    @Override
    @Cacheable(value = CacheConfig.PRESIGNED_DOWNLOAD_URL_CACHE, key = "#userName + ':' + #fileName")
    public String getPresignedDownloadUrl(String userName, String fileName) {
        String key = getS3Key(userName, fileName);

        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15)) // URL expires in 15 mins
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();

        } catch (SdkException e) {
            log.error("Error generating presigned URL for key: {}", key, e);
            throw new StorageException("Error generating direct download link", e);
        }
    }

    @Override
    @CacheEvict(value = CacheConfig.FILE_LIST_CACHE, allEntries = true)
    public void uploadFile(String userName, MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload an empty file.");
        }

        String key = getS3Key(userName, fileName);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            // Stream directly to S3
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        } catch (IOException e) {
            log.error("Failed to read file input stream for user {}", userName, e);
            throw new StorageException("Failed to process file upload", e);
        } catch (SdkException e) {
            log.error("S3 error uploading file for user {}", userName, e);
            throw new StorageException("Failed to store file in S3", e);
        }
    }

    @Override
    public String getPresignedUploadUrl(String userName, String fileName, String contentType) {
        String key = getS3Key(userName, fileName);

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(storageProperties.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .putObjectRequest(putObjectRequest)
                    .build();

            return s3Presigner.presignPutObject(presignRequest).url().toString();

        } catch (SdkException e) {
            log.error("Error generating presigned upload URL for key: {}", key, e);
            throw new StorageException("Error generating direct upload link", e);
        }
    }

    // Security check: prevent path traversal (e.g. "../")
    private void validatePath(String userName, String fileName) {
        if (userName == null || userName.contains("..") || userName.contains("/") || userName.contains("\\")) {
            throw new IllegalArgumentException("Invalid userName format. Path traversal characters are not allowed.");
        }
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid fileName format. Path traversal characters are not allowed.");
        }
    }

    private String getS3Key(String userName, String fileName) {
        validatePath(userName, fileName);
        String prefix = storageProperties.getKeyPrefix();

        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        return String.format(prefix + "/%s/", userName) + fileName;
    }

}
