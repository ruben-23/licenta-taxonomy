//package org.example.jobsmvp.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.client.JdkClientHttpRequestFactory;
//import org.springframework.web.client.RestClient;
//
//@Configuration
//public class RestClientConfig {
//
//    private static final String BASE_URL = "https://jsearch.p.rapidapi.com";
//
//    @Bean
//    public RestClient.Builder restClient() {
//        return RestClient.builder();
//    }
//}

package org.example.jobsmvp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    private static final String BASE_URL = "https://jsearch.p.rapidapi.com";

    @Bean
    public RestClient.Builder restClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        return RestClient.builder()
                .requestFactory(requestFactory);
    }
}