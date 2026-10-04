package com.kobi.territory.notification.application;

/**
 * 보내기 속도 제한 — 초당 maxPerSecond 번을 넘지 않게 앞 요청과의 간격을 맞춘다(발송기 한 스레드, 인스턴스 하나 기준). 푸시 서비스가 몰아
 * 보내는 요청을 막지(429) 않게 하고, 대량 발송이 이 서버의 다른 일을 밀어내지 않게 한다.
 */
final class SendPacer {

    private final long intervalNanos;
    private long nextAt = System.nanoTime();

    SendPacer(int maxPerSecond) {
        this.intervalNanos = 1_000_000_000L / maxPerSecond;
    }

    synchronized void awaitTurn() {
        long now = System.nanoTime();
        long wait = nextAt - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        nextAt = Math.max(now, nextAt) + intervalNanos;
    }
}
