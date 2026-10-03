package com.kobi.territory.api;

import com.kobi.territory.api.security.ExplorerAuthentication;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @CurrentExplorer ExplorerId} 파라미터를 채운다. 4단계부터 인증 공존 — 로그인 세션의 계정 → 그 탐험가, 없으면 X-Explorer-Token
 * 헤더(비밀 접근 토큰, 3단계 결정 2). 판단은 {@link ExplorerAuthentication}.
 * 여러 컨텍스트(탐험·진행·꾸미기·공유)의 컨트롤러가 함께 쓰므로 조립 모듈(app-api)에 둔다 — 컨텍스트끼리 web 패키지를 참조하지 않게(D7).
 * 둘 다 없으면 401 EXPLORER_TOKEN_REQUIRED, 모르는(또는 형식이 틀린) 토큰이면 401 EXPLORER_TOKEN_INVALID, 세션 계정이 없으면 401 ACCOUNT_NOT_FOUND.
 */
class CurrentExplorerArgumentResolver implements HandlerMethodArgumentResolver {

    static final String REQUIRED = ExplorerAuthentication.TOKEN_REQUIRED;
    static final String INVALID = ExplorerAuthentication.TOKEN_INVALID;

    private final ExplorerAuthentication authentication;

    CurrentExplorerArgumentResolver(ExplorerAuthentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentExplorer.class)
            && ExplorerId.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                  WebDataBinderFactory binderFactory) {
        return authentication.require(request.getNativeRequest(HttpServletRequest.class));
    }
}
