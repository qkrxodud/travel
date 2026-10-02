package com.kobi.territory.api;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.ExplorerCredentials;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @CurrentExplorer ExplorerId} 파라미터를 X-Explorer-Token 헤더(비밀 접근 토큰)로 인증해 채운다(3단계 결정 2).
 * 여러 컨텍스트(탐험·진행·꾸미기…)의 컨트롤러가 함께 쓰므로 조립 모듈(app-api)에 둔다 — 컨텍스트끼리 web 패키지를 참조하지 않게(D7).
 * 헤더가 없으면 401 EXPLORER_TOKEN_REQUIRED, 모르는(또는 형식이 틀린) 토큰이면 401 EXPLORER_TOKEN_INVALID.
 */
class CurrentExplorerArgumentResolver implements HandlerMethodArgumentResolver {

    static final String REQUIRED = "EXPLORER_TOKEN_REQUIRED";
    static final String INVALID = "EXPLORER_TOKEN_INVALID";

    private final ExplorerCredentials credentials;

    CurrentExplorerArgumentResolver(ExplorerCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentExplorer.class)
            && ExplorerId.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                  WebDataBinderFactory binderFactory) {
        String token = request.getHeader(CurrentExplorer.HEADER);
        if (token == null || token.isBlank()) {
            throw new TerritoryException(REQUIRED, ErrorKind.UNAUTHENTICATED,
                CurrentExplorer.HEADER + " 헤더로 접근 토큰을 보내 주세요(POST /explorers 로 발급).");
        }
        return credentials.explorerIdByToken(token).map(ExplorerId::of).orElseThrow(() -> new TerritoryException(INVALID,
            ErrorKind.UNAUTHENTICATED, "접근 토큰을 알 수 없어요. 다시 발급해 주세요(POST /explorers)."));
    }
}
