package org.example.jobsmvp.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EmbeddingService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = "http://127.0.0.1:8000";

    public void processStudent(String studentId) {
        String url = baseUrl + "/students/" + studentId + "/process";
        restTemplate.postForObject(url, null, String.class);
    }

    public void processJob(String jobId) {
        String url = baseUrl + "/jobs/" + jobId + "/process";
        restTemplate.postForObject(url, null, String.class);
    }
}