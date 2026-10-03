package com.kobi.territory.exploration.domain.explorer;

import static com.kobi.territory.exploration.domain.Fixtures.NOON;

import com.kobi.territory.common.model.ExplorerId;
import java.time.Duration;
import java.util.random.RandomGenerator;

/** 탐험가 준비 문장: 익명으로 시작한 탐험가, 구글 계정에 연결된 탐험가. */
final class ExplorerFixtures {

    /** 바꾸기 전 핸들 예약 기간 */
    static final Duration RESERVE = Duration.ofDays(30);
    static final AccountIdentity KIM = new AccountIdentity("google", "sub-kim", "Kim.Traveler+tag@Example.com");

    private ExplorerFixtures() {}

    /** 이 기기에서 익명으로 시작한 탐험가. */
    static Explorer anonymous(ExplorerId id) {
        return Explorer.anonymous(id, AccessToken.generate(RandomGenerator.getDefault()).hash(), NOON);
    }

    /** 구글 계정에 연결되고 핸들을 가진 탐험가. */
    static Explorer linked(ExplorerId id, String handle) {
        Explorer explorer = anonymous(id);
        explorer.linkAccount(new Account(new AccountIdentity("google", "sub-" + id.value(), handle + "@example.com"), NOON),
            new Handle(handle));
        return explorer;
    }
}
