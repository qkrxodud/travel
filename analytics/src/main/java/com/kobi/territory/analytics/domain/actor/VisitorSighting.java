package com.kobi.territory.analytics.domain.actor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 방문 한 번 본 사실(화면 이벤트 묶음 하나). 처음 본 날·처음 들어온 길·이어진 탐험가는 <b>한 번만</b> 정해진다 — 처음 본 날은 그 방문의 첫
 * 기록, 들어온 길은 처음 알게 된 값, 탐험가는 처음 이어진 탐험가(같은 기기에서 다른 계정으로 로그인해도 바꾸지 않는다).
 *
 * @param entry        이 묶음의 첫 화면 이벤트가 알려 준 들어온 길(없으면 null)
 * @param explorerHash 이 요청의 탐험가(토큰·세션이 있으면) — 없으면 null
 */
public record VisitorSighting(VisitorId visitorId, Instant seenAt, LocalDate day, EntryPoint entry, ExplorerHash explorerHash,
                              DeviceType device) {
    public VisitorSighting {
        Objects.requireNonNull(visitorId, "visitorId");
        Objects.requireNonNull(seenAt, "seenAt");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(device, "device");
    }
}
