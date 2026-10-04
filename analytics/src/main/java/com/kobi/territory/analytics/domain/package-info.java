/**
 * 분석 도메인(10단계, 순수 Java). 분석은 <b>관찰자</b>다 — 게임 규칙을 바꾸지 않고, 다른 컨텍스트의 사실(공개 이벤트)과 화면 이벤트를 받아
 * 적고 센다. 폴더 하나 = 개념 하나:
 * <ul>
 *   <li>{@code tracking} — 받을 수 있는 이벤트 이름·필드 목록(EventDefinitions)과 검증, 화면 이벤트 묶음(EventBatch), 적는 한 줄(TrackedEvent)</li>
 *   <li>{@code actor} — 누가(익명 방문 ID·탐험가 해시), 어떤 기기·나라(거칠게). 원문 IP·User-Agent·탐험가 id 는 여기서 끝난다</li>
 *   <li>{@code journey} — 탐험가 한 명의 여정 기록(가입일·첫 체크인·초대 합류) — 코호트·퍼널·K 계수의 기준</li>
 *   <li>{@code metrics} — 지표 정의(DAU/WAU/MAU, 퍼널, D1/D7/D30 리텐션, K 계수, 기능별 사용률, 상위 오류 코드)와 집계 기간</li>
 *   <li>{@code ratelimit} — 화면 이벤트 수집의 레이트 리밋(토큰 버킷)</li>
 * </ul>
 */
package com.kobi.territory.analytics.domain;
