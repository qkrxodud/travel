/**
 * 알림 유스케이스(얇은 서비스 — 잠그고 → 불러와서 → 도메인에 시키고 → 저장). 구독·해지·설정, 계정 병합 구독자({@code notification.recipient}),
 * 알림 계획(스케줄 3종 → 받을 사람마다 발송 기록), 발송기(보낼 때가 된 기록을 잡아 보내고 결과로 닫기).
 */
package com.kobi.territory.notification.application;
