package com.kobi.territory.progression.domain;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;

/** 진행 컨텍스트 오류 코드. code 문자열은 API 계약이다. */
public enum ProgressionError {
    TITLE_NOT_FOUND(ErrorKind.NOT_FOUND, "모르는 칭호입니다: %s"),
    TITLE_NOT_EARNED(ErrorKind.RULE_VIOLATION, "아직 얻지 못한 칭호예요: %s"),
    QUEST_NOT_FOUND(ErrorKind.NOT_FOUND, "모르는 퀘스트입니다: %s"),
    QUEST_NOT_COMPLETED(ErrorKind.RULE_VIOLATION, "아직 달성하지 못한 퀘스트예요(%d/%d)."),
    QUEST_ALREADY_CLAIMED(ErrorKind.CONFLICT, "이미 보상을 받은 퀘스트예요."),
    QUEST_BOARD_CLOSED(ErrorKind.RULE_VIOLATION, "지난 달 퀘스트는 바꿀 수 없어요: %s");

    private final ErrorKind kind;
    private final String template;

    ProgressionError(ErrorKind kind, String template) {
        this.kind = kind;
        this.template = template;
    }

    public TerritoryException exception(Object... args) {
        return new TerritoryException(name(), kind, String.format(template, args));
    }
}
