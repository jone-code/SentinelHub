package com.sentinelhub.config;

import com.sentinelhub.storage.MinioStorageService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class MinioInitializer implements ApplicationRunner {

    private final MinioStorageService minioStorageService;

    public MinioInitializer(MinioStorageService minioStorageService) {
        this.minioStorageService = minioStorageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (minioStorageService.isEnabled()) {
            minioStorageService.ensureBucket();
        }
    }
}
