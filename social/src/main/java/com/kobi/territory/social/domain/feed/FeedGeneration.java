package com.kobi.territory.social.domain.feed;

/**
 * 친구 소식 읽기 모델의 세대(5단계 운영 재구성, 리더 결정 4). 조회는 지금 세대(live)만 읽고, 재구성은 다음 세대에 처음부터 쌓은 뒤
 * 다 쌓이면 지금 세대를 바꾼다 — 재구성 중에도 피드가 비지 않고, 실패하면 쌓던 세대만 버려 이전 피드가 그대로 남는다.
 */
public record FeedGeneration(int value) {
    public FeedGeneration {
        if (value < 1) throw new IllegalArgumentException("generation=" + value);
    }

    public FeedGeneration next() {
        return new FeedGeneration(value + 1);
    }
}
