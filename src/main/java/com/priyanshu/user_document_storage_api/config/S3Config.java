package com.priyanshu.user_document_storage_api.config;

import lombok.RequiredArgsConstructor;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
public class S3Config {

    private final StorageProperties storageProperties;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(getCredentialsProvider())
                .serviceConfiguration(getS3Configuration());

        // LocalStack: custom endpoint + static dummy credentials
        if (StringUtils.hasText(storageProperties.getEndpoint())) {
            builder.endpointOverride(URI.create(storageProperties.getEndpoint()))
                    .forcePathStyle(true);
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var presigner = S3Presigner.builder()
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(getCredentialsProvider())
                .serviceConfiguration(getS3Configuration());

        if (StringUtils.hasText(storageProperties.getEndpoint())) {
            presigner.endpointOverride(URI.create(storageProperties.getEndpoint()))
                    .serviceConfiguration(getS3Configuration());
        }

        return presigner.build();
    }

    private AwsCredentialsProvider getCredentialsProvider() {
        if (StringUtils.hasText(storageProperties.getAccessKey())
                && StringUtils.hasText(storageProperties.getSecretKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storageProperties.getAccessKey(),
                            storageProperties.getSecretKey()));
        } else {
            return DefaultCredentialsProvider.create();
        }
    }

    private S3Configuration getS3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(storageProperties.getPathStyleAccessEnabled())
                .build();
    }

}
