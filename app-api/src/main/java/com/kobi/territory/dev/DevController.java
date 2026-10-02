package com.kobi.territory.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 개발·E2E 전용 엔드포인트. local 프로파일에서만 빈이 생성된다.
 * 이후 단계에서 데이터 초기화, 시드 투입, /dev/login 이 이 자리에 들어온다.
 */
@Profile("local")
@RestController
@RequestMapping("/dev")
public class DevController {

    /** 전체 데이터 초기화 자리. 0단계에서는 no-op. */
    @DeleteMapping("/reset")
    public ResponseEntity<Void> reset() {
        return ResponseEntity.noContent().build();
    }

    /** 테스트 시드 투입 자리. 0단계에서는 no-op. */
    @PostMapping("/seed")
    public ResponseEntity<Void> seed() {
        return ResponseEntity.noContent().build();
    }
}
