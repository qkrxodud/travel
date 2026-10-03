package com.kobi.territory.sharing.domain.card;

import java.time.Instant;
import java.util.Objects;

/**
 * 자랑 카드 애그리거트(§2-8) — 렌더 결과(이미지 키)와 그때 기준(CardBasis = 공개 요약 해시 + handle)을 기록한다. 다시 그리기는
 * 링크가 실제 열릴 때만(lazy) 한다.
 *
 * 불변식
 * - 그린 적 없으면 반드시 그린다.
 * - handle(신원)이 바뀌었으면(익명 → 계정, handle 변경) TTL 을 건너뛰고 바로 다시 그린다 — 공유 링크의 og:image 가 남의 이름·
 *   익명 카드로 굳지 않게(QA P2-2).
 * - 요약이 바뀌었으면(낡음) 마지막으로 그린 지 최소 TTL 이 지났을 때만 다시 그린다(그 전에는 그린 카드를 낸다).
 * - 기준이 그대로면 TTL 과 관계없이 다시 그리지 않는다.
 */
public final class ShareCard {

    private final ShareCardId id;
    private CardBasis basis;
    private String imageKey;
    private Instant renderedAt;

    private ShareCard(ShareCardId id, CardBasis basis, String imageKey, Instant renderedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.basis = basis;
        this.imageKey = imageKey;
        this.renderedAt = renderedAt;
    }

    /** 아직 그린 적 없는 카드. */
    public static ShareCard unrendered(ShareCardId id) {
        return new ShareCard(id, null, null, null);
    }

    public static ShareCard restore(ShareCardId id, CardBasis basis, String imageKey, Instant renderedAt) {
        return new ShareCard(id, Objects.requireNonNull(basis, "basis"), Objects.requireNonNull(imageKey, "imageKey"),
            Objects.requireNonNull(renderedAt, "renderedAt"));
    }

    /** 지금 기준(current)으로 다시 그려야 하는지. */
    public boolean needsRender(CardBasis current, Instant now, CardCachePolicy policy) {
        if (!rendered()) return true;
        if (basis.identityDiffers(current)) return true;
        return stale(current) && !now.isBefore(renderedAt.plus(policy.minTtl()));
    }

    /** 그린 뒤 요약이나 handle 이 바뀌었는지(무효화됨). 그린 적 없으면 true. */
    public boolean stale(CardBasis current) {
        return !rendered() || !basis.equals(Objects.requireNonNull(current, "current"));
    }

    /** 그렸다(render): 기준과 그 기준으로 저장한 이미지 키를 기록한다. @return 대체된 예전 이미지 키(없으면 null) */
    public String markRendered(CardBasis renderedBasis, String storedImageKey, Instant at) {
        String previous = imageKey;
        this.basis = Objects.requireNonNull(renderedBasis, "renderedBasis");
        this.imageKey = Objects.requireNonNull(storedImageKey, "storedImageKey");
        this.renderedAt = Objects.requireNonNull(at, "at");
        return storedImageKey.equals(previous) ? null : previous;
    }

    public boolean rendered() {
        return renderedAt != null;
    }

    public ShareCardId id() { return id; }
    public CardBasis basis() { return basis; }
    public String imageKey() { return imageKey; }
    public Instant renderedAt() { return renderedAt; }
}
