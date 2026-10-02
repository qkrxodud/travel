package com.kobi.territory.archfixture.exploration.api;

import com.kobi.territory.archfixture.exploration.domain.Secret;

/** ArchUnit 회귀 픽스처(QA P2-1): api 루트 패키지에 놓여 도메인 타입을 노출하는 클래스 — 금지 목록 방식이 놓쳤던 경우. */
public class Leak {
    public Secret secret;
}
