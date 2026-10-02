package com.kobi.territory.progression.domain.collectionbook;

/** 도감 저장소(지도 단위). 행이 없으면 빈 도감. 평소는 save(변경 반영), 재계산은 replace(지우고 다시 넣음). */
public interface CollectionBookRepository {

    CollectionBook load(String mapId);

    void save(CollectionBook collectionBook);

    void replace(CollectionBook collectionBook);
}
