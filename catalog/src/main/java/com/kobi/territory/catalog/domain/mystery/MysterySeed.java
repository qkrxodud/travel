package com.kobi.territory.catalog.domain.mystery;

import com.kobi.territory.common.model.RegionCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;

/**
 * 미스터리 지역 고르기의 주차 시드(8단계 QA Q1). 서버 비밀값(salt — 설정 territory.mystery.salt, 운영은 환경변수 필수)을 주차와 함께
 * SHA-256 에 넣는다 — 같은 주·같은 비밀값이면 언제 어디서 돌려도 같은 수(결정성), 비밀값을 모르면 공개된 규칙과 통계로 다음 주를 미리 셀 수 없다.
 * 값을 감싸 검증만 하는 값 객체라 record.
 */
public record MysterySeed(String salt) {

    public MysterySeed {
        if (salt == null || salt.isBlank()) throw new IllegalArgumentException("미스터리 시드 비밀값(territory.mystery.salt)이 필요합니다");
    }

    /** 그 주의 고르기 수. */
    public long of(LocalDate weekStart) {
        return hash(weekStart.toString());
    }

    /** 그 주에서 이 지역의 섞기 수(같은 방문자 비율끼리 줄 세울 때 — 주마다 순서가 바뀐다). */
    public long of(LocalDate weekStart, RegionCode region) {
        return hash(weekStart + "|" + region.value());
    }

    private long hash(String subject) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return ByteBuffer.wrap(digest.digest(subject.getBytes(StandardCharsets.UTF_8))).getLong();
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 없음", missing);
        }
    }

    @Override
    public String toString() {
        return "MysterySeed[***]"; // 비밀값은 로그에 남기지 않는다
    }
}
