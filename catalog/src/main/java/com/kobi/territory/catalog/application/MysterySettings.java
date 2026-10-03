package com.kobi.territory.catalog.application;

import com.kobi.territory.catalog.domain.mystery.MysterySeed;

/**
 * 미스터리 지역 설정값(8단계 QA Q1) — app-api 가 territory.mystery.salt 를 바인딩해 넘긴다(local 은 application-local.yml 기본값,
 * 운영은 환경변수 TERRITORY_MYSTERY_SALT 필수 — 없으면 기동 실패).
 */
public record MysterySettings(String salt) {

    /** 주차 시드(비밀값이 비면 거절). */
    public MysterySeed seed() {
        return new MysterySeed(salt);
    }

    @Override
    public String toString() {
        return "MysterySettings[salt=***]";
    }
}
