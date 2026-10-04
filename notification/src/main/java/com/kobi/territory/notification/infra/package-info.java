/**
 * 알림 인프라. {@code entity}: JPA 엔티티(한 테이블 한 파일, 변환은 엔티티가) · {@code repository}: Spring Data 리포지토리와 도메인 포트
 * 어댑터 · {@code webpush}: 보내기 포트 {@code PushSender} 의 웹 푸시 구현(RFC 8291 암호화 + RFC 8292 VAPID, JDK 표준 암호와
 * HttpClient — 외부 라이브러리 없음). entity·repository 는 같은 컨텍스트 infra 밖에서 참조하지 않는다(ArchUnit).
 */
package com.kobi.territory.notification.infra;
