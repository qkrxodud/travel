package com.kobi.territory.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 통합·API 테스트 공통 컨텍스트(local 프로파일 + 가변 시계 + 이벤트 수집기 + MockMvc + 준비 문장 {@link Explorers}).
 * H2 메모리 DB 이름을 따로 둔다 — 같은 JVM의 다른 캐시된 컨텍스트(별도 outbox 릴레이)와 DB를 공유하지 않게.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:territory-it;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import({IntegrationTestConfig.class, Explorers.class})
public @interface IntegrationTest {}
