package com.kobi.territory.exploration.domain.revisit;

import com.kobi.territory.common.model.ExplorerId;

/**
 * 재방문 도장 저장소(revisit_stamp). 도장은 지우지 않으므로 저장은 새 도장 추가뿐이다. 하루 상한을 함께 쓰는 개인 지도 체크인과
 * 직렬화하려고, 도장 커맨드는 개인 지도 territory 행을 먼저 잠근 뒤 불러온다(호출자 몫).
 */
public interface StampBookRepository {

    /** 도장이 하나도 없으면 빈 도장첩. */
    StampBook load(ExplorerId explorerId);

    /** 복원 이후 새로 받은 도장을 넣는다. */
    void save(StampBook stampBook);
}
