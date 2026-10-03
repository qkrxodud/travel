package com.kobi.territory.api;

import com.kobi.territory.api.security.ExplorerAuthentication;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

    private final ExplorerAuthentication authentication;

    WebConfig(ExplorerAuthentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentExplorerArgumentResolver(authentication));
    }
}
