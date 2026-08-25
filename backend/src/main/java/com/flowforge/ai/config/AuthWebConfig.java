package com.flowforge.ai.config;

import com.flowforge.ai.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "flowforge.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthWebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public AuthWebConfig(
            AuthService authService,
            @Value("${flowforge.frontend-url:http://localhost:10086}") String frontendUrl
    ) {
        this.authInterceptor = new AuthInterceptor(authService, frontendUrl);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}
