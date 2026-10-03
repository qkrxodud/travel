package com.kobi.territory.sharing.application;

import com.kobi.territory.sharing.domain.card.CardKind;
import java.time.Instant;

/**
 * 내 카드 메타(GET /me/cards).
 *
 * @param rendered   그린 적 있는지
 * @param stale      그린 뒤 원천이 바뀌었는지(다음에 열릴 때 TTL 이 지났으면 다시 그린다) — 그린 적 없으면 true
 * @param renderedAt 마지막으로 그린 시각, 없으면 null
 */
public record CardMeta(CardKind kind, boolean rendered, boolean stale, Instant renderedAt) {}
