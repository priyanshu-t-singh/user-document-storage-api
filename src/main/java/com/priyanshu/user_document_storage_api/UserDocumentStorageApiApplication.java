package com.priyanshu.user_document_storage_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

import com.priyanshu.user_document_storage_api.config.StorageProperties;

@EnableCaching
@EnableConfigurationProperties(StorageProperties.class)
@SpringBootApplication
public class UserDocumentStorageApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserDocumentStorageApiApplication.class, args);
	}

}
