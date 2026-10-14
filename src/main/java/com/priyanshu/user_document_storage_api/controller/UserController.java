package com.priyanshu.user_document_storage_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    @PostMapping("/{userName}/files")
    public ResponseEntity<String> uploadFile(@PathVariable String userName, @RequestParam MultipartFile file) {
        // TODO: Implement file upload logic here, e.g., save the file to a storage service or database
        return ResponseEntity.ok("File uploaded successfully for user: " + userName);
    }

    @GetMapping("/{userName}/files")
    public String searchFiles(@PathVariable String userName, @RequestParam String searchString, @RequestParam int pageSize) {
        // TODO: Implement file search logic here, e.g., query the storage service or database for files matching the search string
        return new String("Searching for files with search string: " + searchString);
    }

    @GetMapping("/{userName}/files/{fileName}/download")
    public String downloadFile(@PathVariable String userName, @PathVariable String fileName) {
        // TODO: Implement file download logic here, e.g., retrieve the file from a storage service or database and return it as a response
        return new String("Downloading file: " + fileName);
    }

}
