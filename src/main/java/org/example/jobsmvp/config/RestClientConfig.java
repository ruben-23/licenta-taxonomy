package org.example.jobsmvp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private static final String BASE_URL = "https://jsearch.p.rapidapi.com";

    @Bean
    public RestClient.Builder restClient() {
        return RestClient.builder();
    }
}