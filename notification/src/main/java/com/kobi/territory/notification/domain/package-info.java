/**
 * 알림 도메인(12단계, 순수 Java). 여행과 여행 사이에 다시 오게 하는 웹 푸시. 폴더 하나 = 개념 하나:
 * <ul>
 *   <li>{@code recipient} — 알림 받는 사람(PushRecipient): 종류별 켜고 끄기 + 기기(브라우저 구독)들. 기기 수 상한, 같은 브라우저는 한 줄</li>
 *   <li>{@code delivery} — 발송 기록(PushDelivery): 멱등 열쇠(탐험가·종류·기간), 하루 최대 개수, 조용한 시간, 재시도·만료</li>
 *   <li>{@code campaign} — 언제 누구에게 무엇을: 월요일 미스터리, 월말 스트릭 지키기, 계절 테마 시작일 달력과 문구</li>
 *   <li>{@code push} — 보내는 데 쓰는 값(구독 주소·키·메시지)과 보내기 포트(PushSender)·결과</li>
 *   <li>{@code policy} — 여러 곳이 함께 쓰는 알림 종류·조용한 시간</li>
 * </ul>
 */
package com.kobi.territory.notification.domain;
