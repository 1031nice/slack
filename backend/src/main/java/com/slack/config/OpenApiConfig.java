package com.slack.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.parser.OpenAPIV3Parser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        try {
            ClassPathResource resource = new ClassPathResource("api/openapi.yaml");
            String yamlContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            OpenAPIV3Parser parser = new OpenAPIV3Parser();
            return parser.readContents(yamlContent, null, null).getOpenAPI();
        } catch (IOException e) {
            // Fallback to programmatic definition if YAML file not found
            return new OpenAPI()
                    .info(new Info()
                            .title("Slack API")
                            .description("Slack clone application API specification")
                            .version("1.0.0"));
        }
    }
}

