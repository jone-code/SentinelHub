package com.sentinelhub.storage;

import com.sentinelhub.config.MinioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioProperties properties;
    private volatile S3Client client;
    private volatile S3Presigner presigner;

    public MinioStorageService(MinioProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public void ensureBucket() {
        if (!properties.enabled()) {
            return;
        }
        try {
            client().headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (NoSuchBucketException e) {
            client().createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
            log.info("Created MinIO bucket {}", properties.bucket());
        } catch (Exception e) {
            log.warn("MinIO bucket check failed (forensics disabled until available): {}", e.getMessage());
        }
    }

    public void putObject(String objectKey, byte[] data, String contentType) {
        if (!properties.enabled()) {
            throw new IllegalStateException("minio disabled");
        }
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .build();
        client().putObject(request, RequestBody.fromBytes(data));
    }

    public String presignedGetUrl(String objectKey, Duration ttl) {
        if (!properties.enabled()) {
            throw new IllegalStateException("minio disabled");
        }
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(getObjectRequest)
                .build();
        return presigner().presignGetObject(presignRequest).url().toString();
    }

    private S3Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = buildClient();
                }
            }
        }
        return client;
    }

    private S3Presigner presigner() {
        if (presigner == null) {
            synchronized (this) {
                if (presigner == null) {
                    presigner = buildPresigner();
                }
            }
        }
        return presigner;
    }

    private S3Client buildClient() {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private S3Presigner buildPresigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
