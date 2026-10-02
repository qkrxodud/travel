package com.kobi.territory.exploration.api;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.domain.ExplorationError;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class CurrentExplorerArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentExplorer.class)
            && ExplorerId.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                  WebDataBinderFactory binderFactory) {
        String header = request.getHeader(CurrentExplorer.HEADER);
        if (header == null || header.isBlank()) {
            throw ExplorationError.EXPLORER_ID_REQUIRED.exception();
        }
        try {
            return ExplorerId.of(header.strip());
        } catch (IllegalArgumentException e) {
            throw ExplorationError.EXPLORER_ID_REQUIRED.exception();
        }
    }
}
