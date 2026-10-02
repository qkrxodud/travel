package com.kobi.territory.progression.domain;

import static com.kobi.territory.progression.domain.Fixtures.JONGNO;
import static com.kobi.territory.progression.domain.Fixtures.ME;
import static com.kobi.territory.progression.domain.Fixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** D4: 기본 XP 세대 refId — 지역당 회수 안 된 지급 최대 1개, 이벤트 id 없이 멱등. */
class XpLedgerTest {

    @Test
    void 지급_회수_재지급은_세대_번호로_refId가_달라진다() {
        XpLedger ledger = XpLedger.empty();
        assertThat(ledger.grantRegion(ME, JONGNO, 10, T0)).isTrue();
        assertThat(ledger.revokeRegion(ME, JONGNO, T0)).isTrue();
        assertThat(ledger.grantRegion(ME, JONGNO, 10, T0)).isTrue();
        String prefix = "region:" + ME.value() + ":KR-11010#";
        assertThat(ledger.entries()).extracting(XpLedgerEntry::refId)
            .containsExactly(prefix + "1", prefix + "1:revoke", prefix + "2");
        assertThat(ledger.entries()).extracting(XpLedgerEntry::amount).containsExactly(10, -10, 10);
        assertThat(ledger.total()).isEqualTo(10);
    }

    @Test
    void 활성_지급이_있으면_재전달된_지급은_no_op_없으면_재전달된_회수도_no_op() {
        XpLedger ledger = XpLedger.empty();
        ledger.grantRegion(ME, JONGNO, 10, T0);
        assertThat(ledger.grantRegion(ME, JONGNO, 10, T0)).isFalse();
        ledger.revokeRegion(ME, JONGNO, T0);
        assertThat(ledger.revokeRegion(ME, JONGNO, T0)).isFalse();
        assertThat(ledger.entries()).hasSize(2);
        assertThat(ledger.total()).isZero();
    }

    @Test
    void 한번_지급은_refId로_막고_장부_합계가_XP다() {
        XpLedger ledger = XpLedger.empty();
        assertThat(ledger.grantOnce(XpSource.PROVINCE_FIRST, "province:x:KR-11", 15, T0)).isTrue();
        assertThat(ledger.grantOnce(XpSource.PROVINCE_FIRST, "province:x:KR-11", 15, T0)).isFalse();
        assertThat(ledger.total()).isEqualTo(15);
    }

    @Test
    void 복원_데이터의_refId_중복은_거부한다() {
        XpLedgerEntry entry = new XpLedgerEntry(XpSource.QUEST, 60, "quest:x", T0);
        assertThatThrownBy(() -> XpLedger.of(List.of(entry, entry))).isInstanceOf(IllegalStateException.class);
    }
}
