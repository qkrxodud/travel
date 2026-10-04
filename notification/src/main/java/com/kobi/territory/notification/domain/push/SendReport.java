package com.kobi.territory.notification.domain.push;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 일급 컬렉션: 알림 한 건을 한 사람의 기기들에 보낸 결과. */
public final class SendReport {

    private final List<DeviceSend> sends;

    private SendReport(List<DeviceSend> sends) {
        this.sends = List.copyOf(sends);
    }

    public static SendReport of(List<DeviceSend> sends) {
        return new SendReport(sends);
    }

    /** 한 기기라도 받았는지. */
    public boolean anyDelivered() {
        return deliveredCount() > 0;
    }

    public int deliveredCount() {
        return (int) sends.stream().filter(send -> send.outcome() == SendOutcome.DELIVERED).count();
    }

    /** 잠시 뒤 다시 보낼 기기가 있는지. */
    public boolean retryable() {
        return sends.stream().anyMatch(send -> send.outcome() == SendOutcome.RETRY);
    }

    /** 다시 보낼 때까지 기다려야 하는 가장 긴 시간(푸시 서비스가 알려 준 값). */
    public Duration retryAfter() {
        return sends.stream().map(DeviceSend::retryAfter).max(Duration::compareTo).orElse(Duration.ZERO);
    }

    /** 구독이 없어진 기기 주소(지운다). */
    public List<PushEndpoint> goneEndpoints() {
        return sends.stream().filter(send -> send.outcome() == SendOutcome.GONE).map(DeviceSend::endpoint).toList();
    }

    /** 모든 기기의 구독이 없어졌는지(보낼 기기가 하나도 없었던 경우 포함). */
    public boolean allGone() {
        return sends.stream().allMatch(send -> send.outcome() == SendOutcome.GONE);
    }

    /** 기록용 한 줄(예: "DELIVERED 1, GONE 1"). */
    public String summary() {
        return sends.stream().collect(Collectors.groupingBy(DeviceSend::outcome, Collectors.counting())).entrySet().stream()
            .sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + " " + entry.getValue())
            .collect(Collectors.joining(", "));
    }
}
