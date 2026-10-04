package com.kobi.territory.notification.application;

/**
 * VAPID 키 설정값(territory.push.vapid.*) — local 은 application-local.yml 시험용 키, 운영은 환경변수 TERRITORY_VAPID_PUBLIC_KEY ·
 * TERRITORY_VAPID_PRIVATE_KEY · TERRITORY_VAPID_SUBJECT 필수. 형식·한 쌍 여부는 웹 푸시 구현이 기동할 때 확인한다. 개인 키는 로그에 남기지 않는다.
 */
public record VapidCredentials(String publicKey, String privateKey, String subject) {

    @Override
    public String toString() {
        return "VapidCredentials[public=" + publicKey + ", private=****, subject=" + subject + "]";
    }
}
