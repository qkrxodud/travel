/**
 * 소셜 인프라(JPA). {@code entity}: friendship·feed_entry·rank_percentile·region_stats·province_stats 엔티티(도메인 ↔ 행 변환은
 * 엔티티가 한다) · {@code repository}: Spring Data 리포지토리 + 도메인 포트 어댑터("어떻게 저장할지"만). 같은 컨텍스트 infra 밖에서는
 * 참조하지 않는다(ArchUnit social_infra_is_internal).
 */
package com.kobi.territory.social.infra;
