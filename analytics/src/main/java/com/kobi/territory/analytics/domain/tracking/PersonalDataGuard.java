package com.kobi.territory.analytics.domain.tracking;

import java.util.Locale;
import java.util.Set;

/**
 * 개인정보로 보이는 필드 이름. 정의되지 않은 필드는 어차피 거절되지만, 이 이름들은 "개인정보라서"라는 이유를 따로 알려 화면 실수를 빨리
 * 드러낸다. 이름 비교는 대소문자·{@code _}·{@code -} 를 무시한다.
 */
final class PersonalDataGuard {

    private static final Set<String> PERSONAL_KEYS = Set.of("memo", "note", "handle", "nickname", "name", "email", "phone",
        "ip", "ipaddress", "useragent", "ua", "token", "accesstoken", "explorerid", "accountid", "userid", "sessionid",
        "lat", "lng", "lon", "latitude", "longitude", "location", "address", "photo", "photoref", "text", "message");

    private PersonalDataGuard() {}

    static boolean personal(String key) {
        return PERSONAL_KEYS.contains(key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", ""));
    }
}
