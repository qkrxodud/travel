package com.kobi.territory.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kobi.territory.support.Explorers;
import com.kobi.territory.support.Explorers.Anonymous;
import com.kobi.territory.support.IntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 개발용 시드·지우기의 진행 초기화는 몇 번을 해도, 가입 직후 비동기로 도착하는 개인 지도 생성 처리(진행 시작 + 레벨 1 칭호)와 겹쳐도
 * 실패하지 않는다(8단계 QA P2-1 — 빌드 간헐 실패의 재현).
 */
@IntegrationTest
@DisplayName("개발용 진행 초기화")
class ExplorerDataResetTest {

    @Autowired Explorers explorers;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    private int 레벨1칭호(String explorerId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM title_earned WHERE explorer_id = ? AND title_id = 'lv1'", Integer.class,
            explorerId);
    }

    /** 칭호를 지운 바로 다음에 개인 지도 생성 처리가 레벨 1 칭호를 먼저 넣고 커밋하는 순서를 그대로 만든다. */
    private JdbcTemplate 칭호를_지운_직후_개인지도_생성이_끼어드는(String explorerId) {
        return new JdbcTemplate(dataSource) {
            @Override
            public int update(String sql, Object... args) {
                int changed = super.update(sql, args);
                if (sql.startsWith("DELETE FROM title_earned")) {
                    jdbc.update("INSERT INTO title_earned (explorer_id, title_id, earned_at) VALUES (?, 'lv1', CURRENT_TIMESTAMP)",
                        explorerId);
                }
                return changed;
            }
        };
    }

    @Test
    @DisplayName("개인 지도 생성 처리가 초기화 사이에 끼어들어 레벨 1 칭호를 먼저 넣어도 실패하지 않고 칭호는 하나다")
    void interleavedMapCreation() throws Exception {
        Anonymous me = explorers.익명_탐험가();
        explorers.전달이_끝날_때까지();
        ExplorerDataReset reset = new ExplorerDataReset(칭호를_지운_직후_개인지도_생성이_끼어드는(me.id()));

        assertThatCode(() -> reset.reset(me.id())).doesNotThrowAnyException();

        assertThat(레벨1칭호(me.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 번 초기화해도 레벨 1 칭호는 하나다")
    void idempotent() throws Exception {
        Anonymous me = explorers.익명_탐험가();
        explorers.전달이_끝날_때까지();
        ExplorerDataReset reset = new ExplorerDataReset(jdbc);

        reset.reset(me.id());
        reset.reset(me.id());

        assertThat(레벨1칭호(me.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("가입하자마자 기다리지 않고 샘플 영토를 채워도 매번 채워진다")
    void seedRightAfterSignUp() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            Anonymous me = explorers.익명_탐험가();

            explorers.기기로(me, post("/dev/seed")).andExpect(status().isOk());
        }
        explorers.전달이_끝날_때까지();
    }
}
