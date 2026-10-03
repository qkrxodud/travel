/**
 * 소셜 도메인(5단계, 순수 Java). 애그리거트 폴더 하나 = 애그리거트·읽기 모델 하나:
 * <ul>
 *   <li>{@code friendship} — Friendship(팔로우 한 건 = 애그리거트) · Followings · SocialCircle(맞팔로우 = 친구)</li>
 *   <li>{@code feed} — 친구 소식 읽기 모델(이벤트 투영 — 애그리거트 아님, 이벤트로 다시 만들 수 있다)</li>
 *   <li>{@code ranking} — 지도 안 랭킹 · 친구 랭킹(요청 시점 계산)</li>
 *   <li>{@code stats} — 상위 %·지역별 방문자 비율·시·도 평균(일 1회 배치 스냅숏)</li>
 * </ul>
 */
package com.kobi.territory.social.domain;
