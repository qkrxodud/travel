package com.kobi.territory.notification.infra.repository;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.notification.domain.policy.NotificationKind;
import com.kobi.territory.notification.domain.push.PushEndpoint;
import com.kobi.territory.notification.domain.recipient.PushDevice;
import com.kobi.territory.notification.domain.recipient.PushRecipient;
import com.kobi.territory.notification.domain.recipient.PushRecipientRepository;
import com.kobi.territory.notification.infra.entity.PushDeviceJpaEntity;
import com.kobi.territory.notification.infra.entity.PushRecipientJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** 알림 받는 사람 저장소 어댑터 — push_recipient(루트) + push_device. 행 ↔ 도메인 변환은 엔티티가 한다. */
@Repository
class JpaPushRecipientRepository implements PushRecipientRepository {

    /** 탐험가 id(UUID 문자열)보다 앞에 오는 값 — 첫 쪽. */
    private static final String BEFORE_ALL = "";

    private final PushRecipientJpaRepository roots;
    private final PushDeviceJpaRepository deviceRows;

    JpaPushRecipientRepository(PushRecipientJpaRepository roots, PushDeviceJpaRepository deviceRows) {
        this.roots = roots;
        this.deviceRows = deviceRows;
    }

    @Override
    public void ensure(ExplorerId explorerId, Instant at) {
        roots.insertIfAbsent(explorerId.value(), at);
    }

    @Override
    public Optional<PushRecipient> findLocked(ExplorerId explorerId) {
        return roots.lockById(explorerId.value()).map(root -> root.toDomain(deviceRows.findByExplorerId(explorerId.value())));
    }

    @Override
    public Optional<PushRecipient> find(ExplorerId explorerId) {
        return roots.findById(explorerId.value()).map(root -> root.toDomain(deviceRows.findByExplorerId(explorerId.value())));
    }

    /** 루트 칸을 고치고, 뺀 기기는 지우고, 지금 기기는 넣거나 고친다(지우기를 먼저 — 같은 주소를 다시 넣을 때 열쇠가 겹치지 않게). */
    @Override
    public void save(PushRecipient recipient) {
        String explorerId = recipient.explorerId().value();
        roots.findById(explorerId).orElseThrow(() -> new IllegalStateException("알림 루트 행이 없다: " + explorerId)).apply(recipient);
        Map<String, PushDeviceJpaEntity> saved = deviceRows.findByExplorerId(explorerId).stream()
            .collect(Collectors.toMap(PushDeviceJpaEntity::endpointHash, Function.identity()));
        List<PushDeviceJpaEntity> gone = recipient.devices().removed().stream().map(endpoint -> saved.remove(endpoint.fingerprint()))
            .filter(row -> row != null).toList();
        deviceRows.deleteAll(gone);
        deviceRows.flush();
        recipient.devices().stream().forEach(device -> upsert(recipient.explorerId(), device, saved));
        deviceRows.flush();
    }

    private void upsert(ExplorerId owner, PushDevice device, Map<String, PushDeviceJpaEntity> saved) {
        PushDeviceJpaEntity row = saved.get(device.endpoint().fingerprint());
        if (row == null) deviceRows.save(PushDeviceJpaEntity.from(owner, device));
        else row.apply(owner, device);
    }

    @Override
    public void releaseEndpoint(PushEndpoint endpoint, ExplorerId keeper) {
        deviceRows.deleteOthers(endpoint.fingerprint(), keeper.value());
    }

    @Override
    public List<ExplorerId> reachable(NotificationKind kind, ExplorerId after, int limit) {
        String from = after == null ? BEFORE_ALL : after.value();
        PageRequest page = PageRequest.of(0, limit);
        List<String> ids = switch (kind) {
            case WEEKLY_MYSTERY -> roots.reachableForMystery(from, page);
            case STREAK_GUARD -> roots.reachableForStreak(from, page);
            case SEASON_START -> roots.reachableForSeason(from, page);
        };
        return ids.stream().map(ExplorerId::of).toList();
    }
}
