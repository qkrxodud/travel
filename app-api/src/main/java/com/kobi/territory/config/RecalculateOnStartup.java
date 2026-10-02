package com.kobi.territory.config;

import com.kobi.territory.progression.application.RecalculateService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 재계산 배치의 운영 진입점(관리용). HTTP 로 열지 않고 기동 인자로만 켠다:
 * {@code java -jar territory.jar --territory.progression.recalculate-on-startup=true}. local 은 POST /dev/recalculate.
 */
@Component
@ConditionalOnProperty(name = "territory.progression.recalculate-on-startup", havingValue = "true")
class RecalculateOnStartup implements ApplicationRunner {

    private final RecalculateService recalculate;

    RecalculateOnStartup(RecalculateService recalculate) {
        this.recalculate = recalculate;
    }

    @Override
    public void run(ApplicationArguments args) {
        recalculate.recalculateAll();
    }
}
