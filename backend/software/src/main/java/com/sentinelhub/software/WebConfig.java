package com.sentinelhub.software;

import com.sentinelhub.common.web.HealthController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Bean
    public HealthController healthController() {
        return new HealthController("software");
    }
}
