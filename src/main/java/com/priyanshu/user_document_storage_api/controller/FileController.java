package com.priyanshu.user_document_storage_api.controller;

import java.util.Map;

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

import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/users/{userName}/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadFile(
            @PathVariable String userName,
            @RequestParam MultipartFile file) {

        fileStorageService.uploadFile(userName, file);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{fileName}/upload-link")
    public ResponseEntity<Map<String, String>> getPresignedUploadUrl(
            @PathVariable String userName,
            @PathVariable String fileName,
            @RequestParam(defaultValue = "application/octet-stream") String contentType) {

        String url = fileStorageService.getPresignedUploadUrl(userName, fileName, contentType);
        return ResponseEntity.ok(Map.of("uploadUrl", url));
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<FileMetadata>> searchFiles(
            @PathVariable String userName,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String continuationToken) {

        return ResponseEntity.ok(
                fileStorageService.searchFiles(userName, search, pageSize, continuationToken));
    }

    @GetMapping("/{fileName}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String userName,
            @PathVariable String fileName) {

        Resource resource = fileStorageService.downloadFile(userName, fileName);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @GetMapping("/{fileName}/download-link")
    public ResponseEntity<Map<String, String>> getPresignedUrl(
            @PathVariable String userName,
            @PathVariable String fileName) {

        String url = fileStorageService.getPresignedDownloadUrl(userName, fileName);
        return ResponseEntity.ok(Map.of("downloadUrl", url));
    }

}
