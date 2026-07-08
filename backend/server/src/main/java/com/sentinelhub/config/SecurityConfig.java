package com.sentinelhub.config;

import com.sentinelhub.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/ready", "/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/v1/auth/login").permitAll()
                        .requestMatchers("/api/client/v1/service/register", "/api/client/v1/service/heartbeat",
                                "/api/client/v1/service/info", "/api/client/v1/service/report/assets",
                                "/api/client/v1/service/report/events",
                                "/api/client/v1/service/report/compliance",
                                "/api/client/v1/service/report/nac-status",
                                "/api/client/v1/service/policy-bundle",
                                "/api/client/v1/service/compliance-baseline",
                                "/api/client/v1/service/dlp-rules",
                                "/api/client/v1/service/nac-policy").permitAll()
                        .requestMatchers("/api/client/v1/info", "/api/client/v1/status").permitAll()
                        .requestMatchers("/api/admin/v1/**").authenticated()
                        .requestMatchers("/api/app/v1/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
