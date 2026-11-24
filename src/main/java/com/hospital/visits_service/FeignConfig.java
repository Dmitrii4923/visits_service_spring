package com.hospital.visits_service;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AbstractOAuth2Token;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor jwtRelayInterceptor() {
        return template -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getCredentials() instanceof AbstractOAuth2Token token) {
                template.header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getTokenValue());
            }
        };
    }
}
