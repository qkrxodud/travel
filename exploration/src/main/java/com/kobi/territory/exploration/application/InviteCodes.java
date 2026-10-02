package com.kobi.territory.exploration.application;

import com.kobi.territory.exploration.domain.map.ExpeditionMapRepository;
import com.kobi.territory.exploration.domain.map.InviteCode;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** 새 초대코드 발급 — 저장소에 없는 코드를 고른다(마지막 방어는 DB UNIQUE). */
@Component
public class InviteCodes {

    private static final int ATTEMPTS = 10;

    private final ExpeditionMapRepository maps;
    private final SecureRandom random = new SecureRandom();

    public InviteCodes(ExpeditionMapRepository maps) {
        this.maps = maps;
    }

    public InviteCode issue() {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            InviteCode code = InviteCode.generate(random);
            if (!maps.existsByInviteCode(code)) return code;
        }
        throw new IllegalStateException("초대코드 생성 실패(충돌 반복)");
    }
}
