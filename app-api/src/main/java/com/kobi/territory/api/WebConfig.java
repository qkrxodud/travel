package com.kobi.territory.api;

import com.kobi.territory.exploration.api.query.ExplorerCredentials;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

    private final ExplorerCredentials credentials;

    WebConfig(ExplorerCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentExplorerArgumentResolver(credentials));
    }
}
