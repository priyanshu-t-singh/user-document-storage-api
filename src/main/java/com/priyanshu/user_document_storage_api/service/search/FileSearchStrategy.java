package com.priyanshu.user_document_storage_api.service.search;

public interface FileSearchStrategy {
    boolean matches(String fileName, String searchTerm);
}
