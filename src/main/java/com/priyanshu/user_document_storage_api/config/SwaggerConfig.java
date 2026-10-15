package com.priyanshu.user_document_storage_api.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;

@Configuration
@OpenAPIDefinition( //
        info = @Info( //
                title = "User Document Storage API", //
                description = "REST API for uploading, retrieving, and managing user documents securely.", //
                version = "1.2.1", //
                contact = @Contact( //
                        name = "Priyanshu", //
                        email = "priyanshu-t@outlook.com" //
                ) //
        ) //
)
public class SwaggerConfig {
}
