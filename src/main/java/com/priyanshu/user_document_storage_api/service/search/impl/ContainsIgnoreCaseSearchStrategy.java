package com.priyanshu.user_document_storage_api.service.search.impl;

import org.springframework.stereotype.Component;

import com.priyanshu.user_document_storage_api.service.search.FileSearchStrategy;

@Component
public class ContainsIgnoreCaseSearchStrategy implements FileSearchStrategy {

    @Override
    public boolean matches(String fileName, String searchTerm) {
        if (fileName == null || searchTerm == null) {
            return false;
        }
        return fileName.toLowerCase().contains(searchTerm.toLowerCase());
    }

}
