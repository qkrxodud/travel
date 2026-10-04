package com.kobi.territory.analytics.domain.tracking;

import com.kobi.territory.analytics.domain.actor.ActorKey;
import com.kobi.territory.analytics.domain.actor.Country;
import com.kobi.territory.analytics.domain.actor.DeviceType;
import com.kobi.territory.analytics.domain.actor.ExplorerHash;
import com.kobi.territory.analytics.domain.actor.VisitorId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 적어 두는 이벤트 한 줄(원본 — 보관 기간이 지나면 지운다). 개인정보는 없다: 탐험가는 해시, 방문은 무작위 ID, 기기·나라는 거칠게.
 *
 * @param day          서울 기준 날짜(지표의 "하루")
 * @param actor        한 사람으로 세는 열쇠 — 공개 페이지 열람처럼 누구인지 모르는 요청은 null(사람 수 지표에 안 들어간다)
 * @param explorerHash 이어진 탐험가(없으면 null)
 * @param visitorId    화면 이벤트의 방문 ID(서버 사실·요청은 null)
 * @param country      나라(모르면 null)
 * @param label        집계 갈래 값(탭 이름·오류 코드·합류 경로 등 — 정의의 labelField 값, 없으면 null)
 * @param dedupKey     같은 사실을 두 번 적지 않기 위한 지문(서버 사실만, 화면 이벤트는 null)
 */
public record TrackedEvent(String name, EventSource source, Instant occurredAt, LocalDate day, ActorKey actor,
                           ExplorerHash explorerHash, VisitorId visitorId, DeviceType device, Country country, String label,
                           EventProperties properties, String dedupKey) {

    public TrackedEvent {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(properties, "properties");
    }
}
