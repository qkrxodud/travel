package com.kobi.territory.sharing.application;

import com.kobi.territory.exploration.api.query.ProfileMapView;
import com.kobi.territory.sharing.domain.showcase.Showcase;
import java.time.Year;
import java.util.List;

/**
 * 공개 프로필(/u/{handle}) 한 장의 내용: 공개 정보 + 프로필 링크로 합류할 수 있는 공유 지도(초대코드 없음).
 *
 * @param year 리캡 기준 연도(서버 시계)
 */
public record PublicProfile(Showcase showcase, List<ProfileMapView> joinableMaps, Year year) {
    public PublicProfile {
        joinableMaps = List.copyOf(joinableMaps);
    }
}
