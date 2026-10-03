package com.kobi.territory.sharing.domain.showcase;

/** 카드 렌더러 포트(PNG). 구현은 infra(Java2D headless). */
public interface CardRenderer {

    /** @return PNG 바이트 */
    byte[] render(CardContent content);
}
