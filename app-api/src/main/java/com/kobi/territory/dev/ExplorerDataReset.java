package com.kobi.territory.dev;

import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * dev 시드·전부 지우기 때 한 탐험가의 진행·가방을 시작 상태로 되돌린다(local 전용 — DevController 가 쓴다).
 * <p>
 * 멱등(8단계 QA P2-1): 가입 직후 시드하면 릴레이가 비동기로 처리하는 개인 지도 생성(MapCreated → 진행 루트 + 레벨 1 칭호)이 이 초기화의
 * "칭호 지우기"와 "레벨 1 칭호 넣기" 사이에 끼어들 수 있다. 그래서 레벨 1 칭호는 <b>이미 있으면 건너뛴다</b>(넣기 전 확인 + 그 사이 다른
 * 쪽이 먼저 넣어 유일성 위반이 나도 목표 상태라 무시). 각 문장은 자동 커밋이다(긴 트랜잭션으로 릴레이 처리를 막지 않는다).
 * 루트 행(explorer_progress·inventory·scene)은 지우지 않는다 — 루트 선생성(S3-1) 전제 유지(Q-R2-2).
 */
class ExplorerDataReset {

    /** 진행 자식 행(8단계 보호권 장부 포함). */
    static final List<String> PROGRESSION_TABLES = List.of("xp_ledger", "badge_earned", "title_earned",
        "explorer_region_mark", "explorer_region", "quest_progress", "streak_freeze");

    /** 꾸미기 자식 행 — 시드·전부 지우기 때 가방도 비운다(세트 배경처럼 회수 없는 보상이 남지 않게). */
    static final List<String> WARDROBE_CHILD_TABLES = List.of("owned_item_basis", "owned_item", "inventory_visit");

    private final JdbcTemplate jdbc;

    ExplorerDataReset(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void reset(String explorerId) {
        PROGRESSION_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table + " WHERE explorer_id = ?", explorerId));
        // 진행 루트 행은 지우지 않고 시작 상태로 되돌린다. version 을 올려 동시 갱신을 드러낸다.
        jdbc.update("UPDATE explorer_progress SET xp = 0, level = 1, title_id = NULL, streak_months = 0, streak_last_month = NULL, "
            + "version = version + 1 WHERE explorer_id = ?", explorerId);
        grantLevelOneTitle(explorerId);
        // 개인 지도 도감만 — 공유 지도 도감은 다른 멤버 것이기도 하다(QA P3-13)
        jdbc.update("DELETE FROM set_progress WHERE map_id IN (SELECT id FROM expedition_map WHERE owner_id = ? AND kind = 'PERSONAL')",
            explorerId);
        WARDROBE_CHILD_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table + " WHERE explorer_id = ?", explorerId));
        jdbc.update("UPDATE scene SET slot_hat = NULL, slot_hand = NULL, slot_badge = NULL, slot_bag = NULL, slot_pet = NULL, "
            + "slot_bg = NULL, props = '', version = version + 1 WHERE explorer_id = ?", explorerId);
    }

    /** 시작 상태의 레벨 1 칭호(ExplorerProgress.start 와 같게) — 이미 있으면 건너뛴다. 진행 루트가 아직 없으면 루트를 만들 때 함께 생긴다. */
    private void grantLevelOneTitle(String explorerId) {
        try {
            jdbc.update("INSERT INTO title_earned (explorer_id, title_id, earned_at) SELECT explorer_id, 'lv1', updated_at "
                + "FROM explorer_progress WHERE explorer_id = ? "
                + "AND NOT EXISTS (SELECT 1 FROM title_earned WHERE explorer_id = ? AND title_id = 'lv1')", explorerId, explorerId);
        } catch (DuplicateKeyException alreadyGranted) {
            // 확인과 넣기 사이에 개인 지도 생성 처리가 먼저 넣었다 — 이미 목표 상태
        }
    }
}
