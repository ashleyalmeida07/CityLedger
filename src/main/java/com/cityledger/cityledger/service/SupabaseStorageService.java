package com.cityledger.cityledger.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Value("${supabase.bucket}")
    private String bucketName;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Uploads a file to Supabase Storage and returns the public URL.
     *
     * @param file The uploaded multipart file
     * @param complaintId The complaint ID (used to namespace the file)
     * @return Public URL of the uploaded file, or null if upload fails
     */
    public String uploadFile(MultipartFile file, Long complaintId) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (supabaseUrl == null || supabaseUrl.contains("YOUR_")) {
            log.warn("Supabase not configured. Skipping file upload.");
            return null;
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // Unique path: complaints/{complaintId}/{uuid}.ext
            String filePath = "complaints/" + complaintId + "/" + UUID.randomUUID() + extension;

            // Supabase Storage REST API: POST /storage/v1/object/{bucket}/{path}
            String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filePath;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseKey);
            headers.set("apikey", supabaseKey);
            headers.setContentType(MediaType.valueOf(
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream"
            ));

            HttpEntity<byte[]> request = new HttpEntity<>(file.getBytes(), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // Build public URL
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + filePath;
                log.info("File uploaded to Supabase: {}", publicUrl);
                return publicUrl;
            } else {
                log.error("Supabase upload failed with status: {}", response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("Supabase upload exception: {}", e.getMessage());
            return null;
        }
    }
}
