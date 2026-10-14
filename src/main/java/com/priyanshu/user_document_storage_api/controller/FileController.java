package com.priyanshu.user_document_storage_api.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.priyanshu.user_document_storage_api.dto.FileMetadata;
import com.priyanshu.user_document_storage_api.dto.PaginatedResponse;
import com.priyanshu.user_document_storage_api.service.FileStorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users/{userName}/files")
@Tag(name = "File Management", description = "Endpoints for uploading and downloading user files")
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file for a specific user", description = "Uploads a file to the storage for the specified user.")
    public ResponseEntity<Void> uploadFile(
            @PathVariable String userName,
            @RequestParam MultipartFile file) {

        fileStorageService.uploadFile(userName, file);
        return ResponseEntity.ok().build();
    }

    private record UploadResponse(String uploadUrl) {
    }

    @GetMapping("/{fileName}/upload-link")
    @Operation(summary = "Get a presigned URL for uploading a file", description = "Generates a presigned URL that allows the client to upload a file directly to the storage without going through the server.")
    public ResponseEntity<UploadResponse> getPresignedUploadUrl(
            @PathVariable String userName,
            @PathVariable String fileName,
            @RequestParam(defaultValue = "application/octet-stream") String contentType) {

        String url = fileStorageService.getPresignedUploadUrl(userName, fileName, contentType);
        return ResponseEntity.ok(new UploadResponse(url));
    }

    @GetMapping
    @Operation(summary = "Search for files", description = "Searches for files associated with a specific user.")
    public ResponseEntity<PaginatedResponse<FileMetadata>> searchFiles(
            @PathVariable String userName,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String continuationToken) {

        return ResponseEntity.ok(
                fileStorageService.searchFiles(userName, search, pageSize, continuationToken));
    }

    @GetMapping("/{fileName}/download")
    @Operation(summary = "Download a file for a specific user", description = "Downloads a file from the storage for the specified user.")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String userName,
            @PathVariable String fileName) {

        Resource resource = fileStorageService.downloadFile(userName, fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    private record PresignedUrlResponse(String url) {
    }

    @GetMapping("/{fileName}/download-link")
    @Operation(summary = "Get a presigned URL for downloading a file", description = "Generates a presigned URL that allows the client to download a file directly from the storage without going through the server.")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
            @PathVariable String userName,
            @PathVariable String fileName) {

        String url = fileStorageService.getPresignedDownloadUrl(userName, fileName);
        return ResponseEntity.ok(new PresignedUrlResponse(url));
    }

}
