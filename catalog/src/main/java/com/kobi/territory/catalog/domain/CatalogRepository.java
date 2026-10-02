package com.kobi.territory.catalog.domain;

/** 카탈로그 참조 데이터 저장소(시작 시 한 번 로드). */
public interface CatalogRepository {
    Catalog load();
}
