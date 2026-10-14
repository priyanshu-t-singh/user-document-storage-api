package com.priyanshu.user_document_storage_api.service;

public interface FileSearchStrategy {
    boolean matches(String fileName, String searchTerm);
}
