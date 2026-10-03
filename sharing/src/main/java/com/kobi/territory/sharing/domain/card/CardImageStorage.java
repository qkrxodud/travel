package com.kobi.territory.sharing.domain.card;

import java.util.Optional;

/**
 * 카드 이미지 저장소 포트. local 은 파일시스템(설정 경로, build/ 밖), 운영은 이후 오브젝트 스토리지. 캐시이므로 잃어버려도 된다 —
 * 없으면 다시 그린다.
 */
public interface CardImageStorage {

    /** key 에 PNG 를 쓴다(있으면 덮어쓴다). @return 저장한 키 */
    String store(String key, byte[] png);

    Optional<byte[]> load(String key);

    /** 더 쓰지 않는 이미지를 지운다(없으면 무시). */
    void delete(String key);
}
