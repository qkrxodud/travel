package com.kobi.territory.notification.infra.repository;

import com.kobi.territory.notification.infra.entity.PushDeviceJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PushDeviceJpaRepository extends JpaRepository<PushDeviceJpaEntity, String> {

    List<PushDeviceJpaEntity> findByExplorerId(String explorerId);

    /** 이 주소의 기기가 다른 탐험가 것이면 지운다. @return 지운 행 수 */
    @Modifying
    @Query("delete from PushDeviceJpaEntity device where device.endpointHash = :hash and device.explorerId <> :keeper")
    int deleteOthers(@Param("hash") String hash, @Param("keeper") String keeper);
}
