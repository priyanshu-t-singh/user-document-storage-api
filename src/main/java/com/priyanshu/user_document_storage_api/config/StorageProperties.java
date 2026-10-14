package com.priyanshu.user_document_storage_api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "storage.s3")
public class StorageProperties {
    private String bucketName;
    private String region;
}
