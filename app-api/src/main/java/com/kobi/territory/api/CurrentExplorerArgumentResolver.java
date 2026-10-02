package com.kobi.territory.api;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@code @CurrentExplorer ExplorerId} 파라미터를 X-Explorer-Id 헤더에서 채운다. 여러 컨텍스트(탐험·진행…)의 컨트롤러가
 * 함께 쓰므로 조립 모듈(app-api)에 둔다 — 컨텍스트끼리 web 패키지를 참조하지 않게(D7).
 * 헤더가 없거나 UUID가 아니면 401 EXPLORER_ID_REQUIRED.
 */
class CurrentExplorerArgumentResolver implements HandlerMethodArgumentResolver {

    static final String CODE = "EXPLORER_ID_REQUIRED";
    static final String MESSAGE = "X-Explorer-Id 헤더로 탐험가를 알려 주세요(POST /explorers 로 발급).";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentExplorer.class)
            && ExplorerId.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                  WebDataBinderFactory binderFactory) {
        String header = request.getHeader(CurrentExplorer.HEADER);
        try {
            if (header != null && !header.isBlank()) return ExplorerId.of(header.strip());
        } catch (IllegalArgumentException malformed) {
            // 형식 오류도 아래 401로
        }
        throw new TerritoryException(CODE, ErrorKind.UNAUTHENTICATED, MESSAGE);
    }
}
