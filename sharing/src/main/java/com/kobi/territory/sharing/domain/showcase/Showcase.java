package com.kobi.territory.sharing.domain.showcase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 한 탐험가의 공개 정보 묶음 — 공개 프로필 페이지와 자랑 카드가 보여 줄 수 있는 전부(색칠·집계, 월 단위 시기, 진행·장면 요약).
 * 메모·사진·정확한 날짜는 여기 들어올 길이 없다(§7).
 *
 * @param handle 공개 handle, 익명 탐험가(내 카드 미리보기)는 null
 */
public record Showcase(String handle, RegionAtlas atlas, PublicVisits visits, ShowcaseProgress progress, ShowcaseScene scene) {
    public Showcase {
        Objects.requireNonNull(atlas, "atlas");
        Objects.requireNonNull(visits, "visits");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(scene, "scene");
    }

    /**
     * 실제로 그리는 공개 요약의 해시(SHA-256 hex) — 카드 기준(QA P2-1). salt 에 카드 종류·기준 연도를 넣는다. 진행·가방이 이벤트
     * 없이 바뀌어도(재계산·병합) 요약이 바뀌면 해시가 바뀐다.
     */
    public String summaryHash(String salt) {
        return sha256(salt + "|" + fingerprint());
    }

    /** 두 탐험가 요약을 함께(VS 카드). */
    public String pairHash(Showcase theirs, String salt) {
        return sha256(salt + "|" + fingerprint() + "||" + theirs.fingerprint());
    }

    private String fingerprint() {
        return handle + "|" + visits.fingerprint() + "|" + progress + "|" + scene;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 없음", impossible);
        }
    }

    public int conquestPercent() {
        return visits.conquestPercent(atlas);
    }

    public int conqueredProvinces() {
        return visits.conqueredProvinceCount(atlas);
    }

    /** 카드에 찍는 이름 "@handle" — 익명 탐험가(handle 없음, 내 카드 미리보기)는 "나". */
    public String displayName() {
        return handle == null ? "나" : "@" + handle;
    }

    /** 카드 아래 문구 — 공개 프로필 경로 "/u/{handle}", 익명이면 로그인 안내. */
    public String footer() {
        return handle == null ? "나의 영토 · 로그인하면 공개 프로필 링크가 생겨요" : "나의 영토 /u/" + handle;
    }
}
