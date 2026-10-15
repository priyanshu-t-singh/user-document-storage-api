package com.priyanshu.user_document_storage_api.controller;

import com.priyanshu.user_document_storage_api.dto.FileMetadata;
import com.priyanshu.user_document_storage_api.dto.PaginatedResponse;
import com.priyanshu.user_document_storage_api.exception.FileNotFoundException;
import com.priyanshu.user_document_storage_api.exception.GlobalExceptionHandler;
import com.priyanshu.user_document_storage_api.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ FileController.class, GlobalExceptionHandler.class })
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileStorageService fileStorageService;

    // 1. Test Search Endpoint
    @Test
    void searchFiles_Returns200_WithPaginatedData() throws Exception {
        FileMetadata file = FileMetadata.builder()
                .fileName("logistics_report.pdf")
                .sizeBytes(2048L)
                .lastModified(Instant.now())
                .build();

        PaginatedResponse<FileMetadata> response = new PaginatedResponse<>(List.of(file), "nextToken123");

        when(fileStorageService.searchFiles(eq("sandy"), eq("logistics"), eq(50), isNull()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/users/sandy/files")
                .param("search", "logistics")
                .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].fileName").value("logistics_report.pdf"))
                .andExpect(jsonPath("$.items[0].sizeBytes").value(2048))
                .andExpect(jsonPath("$.continuationToken").value("nextToken123"));
    }

    // 2. Test Stream Download Endpoint
    @Test
    void downloadFile_Returns200_WithFileStream() throws Exception {
        byte[] fileContent = "dummy pdf content".getBytes();
        ByteArrayResource resource = new ByteArrayResource(fileContent);

        when(fileStorageService.downloadFile("sandy", "report.pdf"))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/users/sandy/files/report.pdf/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\""))
                .andExpect(content().bytes(fileContent));
    }

    // 3. Test Presigned Download Link Endpoint
    @Test
    void getPresignedDownloadUrl_Returns200_WithUrl() throws Exception {
        String fakeAwsUrl = "https://my-bucket.s3.amazonaws.com/users/sandy/report.pdf?X-Amz-Signature=...";

        when(fileStorageService.getPresignedDownloadUrl("sandy", "report.pdf"))
                .thenReturn(fakeAwsUrl);

        mockMvc.perform(get("/api/v1/users/sandy/files/report.pdf/download-link"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value(fakeAwsUrl));
    }

    // 4. Test Multipart Upload Endpoint
    @Test
    void uploadFile_Returns200_OnSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "dummy content".getBytes());

        // No 'when' needed since uploadFile returns void, we just verify it doesn't
        // throw
        mockMvc.perform(multipart("/api/v1/users/sandy/files")
                .file(file))
                .andExpect(status().isOk());
    }

    // 5. Test Presigned Upload Link Endpoint
    @Test
    void getPresignedUploadUrl_Returns200_WithUrl() throws Exception {
        String fakeAwsUrl = "https://my-bucket.s3.amazonaws.com/users/sandy/report.pdf?X-Amz-Signature=...";

        when(fileStorageService.getPresignedUploadUrl("sandy", "report.pdf", "application/pdf"))
                .thenReturn(fakeAwsUrl);

        mockMvc.perform(get("/api/v1/users/sandy/files/report.pdf/upload-link")
                .param("contentType", "application/pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value(fakeAwsUrl));
    }

    // 6. Test Exception Handler (404 Not Found)
    @Test
    void downloadFile_Returns404_WhenFileNotFound() throws Exception {
        when(fileStorageService.downloadFile("sandy", "missing.pdf"))
                .thenThrow(new FileNotFoundException("File not found: missing.pdf"));

        mockMvc.perform(get("/api/v1/users/sandy/files/missing.pdf/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("File not found: missing.pdf"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // 7. Test Exception Handler (400 Bad Request / Security)
    @Test
    void getPresignedUploadUrl_Returns400_OnPathTraversalAttempt() throws Exception {
        when(fileStorageService.getPresignedUploadUrl("sandy", "../secret.txt", "text/plain"))
                .thenThrow(new IllegalArgumentException(
                        "Invalid fileName format. Path traversal characters are not allowed."));

        mockMvc.perform(get("/api/v1/users/sandy/files/../secret.txt/upload-link")
                .param("contentType", "text/plain"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400));
    }
}