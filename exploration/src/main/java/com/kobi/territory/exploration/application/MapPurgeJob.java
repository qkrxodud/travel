package com.kobi.territory.exploration.application;

import com.kobi.territory.exploration.domain.map.MapId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 유예 종료 배치(territory.map.leave-grace-days, 기본 7일). 주기마다 유예가 끝난 탈퇴 기록이 있는 지도를 찾아
 * 지도마다 따로(트랜잭션 분리) 기록을 지우고 MemberPurged 를 낸다 — 숨긴 방문은 그 구독자가 하드 삭제한다.
 */
@Component
public class MapPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(MapPurgeJob.class);

    private final MapService maps;

    public MapPurgeJob(MapService maps) {
        this.maps = maps;
    }

    @Scheduled(fixedDelayString = "${territory.map.purge-interval-ms:600000}", initialDelayString = "${territory.map.purge-interval-ms:600000}")
    public int run() {
        int purged = 0;
        for (MapId mapId : maps.mapsWithExpiredDepartures()) {
            try {
                purged += maps.purgeExpired(mapId);
            } catch (RuntimeException exception) {
                log.error("탈퇴 유예 종료 처리 실패(다음 지도로 계속): {} — {}", mapId, exception.toString());
            }
        }
        if (purged > 0) log.info("탈퇴 유예 종료: {}건 하드 삭제 예약(MemberPurged)", purged);
        return purged;
    }
}
