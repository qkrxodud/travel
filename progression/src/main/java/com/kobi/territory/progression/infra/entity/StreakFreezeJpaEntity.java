package com.kobi.territory.progression.infra.entity;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.progression.domain.progress.FreezeReason;
import com.kobi.territory.progression.domain.progress.StreakFreezeEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.YearMonth;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** streak_freeze — 보호권 장부 한 줄(8단계 V6, ExplorerProgress 의 자식). ref_id UNIQUE 가 멱등 키. 받은 줄 양수(상한이면 0)·쓴 줄 음수. */
@Entity
@Table(name = "streak_freeze")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StreakFreezeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "explorer_id", nullable = false, length = 36)
    private String explorerId;

    @Column(name = "ref_id", nullable = false, unique = true, length = 160)
    private String refId;

    @Column(nullable = false, length = 20)
    private String reason;

    @Column(nullable = false)
    private int amount;

    @Column(name = "used_month", length = 7)
    private String usedMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static StreakFreezeJpaEntity from(ExplorerId explorer, StreakFreezeEntry entry) {
        StreakFreezeJpaEntity entity = new StreakFreezeJpaEntity();
        entity.explorerId = explorer.value();
        entity.refId = entry.refId();
        entity.reason = entry.reason().name();
        entity.amount = entry.amount();
        entity.usedMonth = entry.month() == null ? null : entry.month().toString();
        entity.createdAt = entry.at();
        return entity;
    }

    public StreakFreezeEntry toDomain() {
        return new StreakFreezeEntry(refId, FreezeReason.valueOf(reason), amount,
            usedMonth == null ? null : YearMonth.parse(usedMonth), createdAt);
    }
}
