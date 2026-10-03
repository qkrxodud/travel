package com.kobi.territory.sharing.domain.card;

import java.util.Optional;

/** 자랑 카드 저장소(share_card, version 낙관적 락). */
public interface ShareCardRepository {

    Optional<ShareCard> find(ShareCardId id);

    void save(ShareCard card);
}
