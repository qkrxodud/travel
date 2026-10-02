package com.kobi.territory.exploration.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청의 X-Explorer-Id 헤더를 ExplorerId로 받는다. 헤더가 없거나 형식이 틀리면 401 EXPLORER_ID_REQUIRED.
 * 존재 여부(404 EXPLORER_NOT_FOUND)는 유스케이스가 확인한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentExplorer {
    String HEADER = "X-Explorer-Id";
}
