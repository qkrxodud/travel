package com.kobi.territory.progression.domain;

/** 도감 저장소(지도 단위). 행이 없으면 빈 도감. */
public interface CollectionBookRepository {

    CollectionBook load(String mapId);

    void save(CollectionBook collection);
}
