package com.kobi.territory.exploration.domain;

import java.time.LocalDate;

/**
 * 방문 기록 부분 수정 값. 생략(null)한 항목은 기존 값을 유지한다. 메모는 ""로 지우고, 사진은 ""로 지운다.
 */
public record VisitPatch(VisitDate date, Memo memo, PhotoRef photo, boolean photoGiven) {

    public static VisitPatch of(LocalDate date, String memo, String photoUrl) {
        return new VisitPatch(date == null ? null : VisitDate.of(date), memo == null ? null : Memo.of(memo),
            PhotoRef.ofNullable(photoUrl), photoUrl != null);
    }

    VisitDate dateOr(VisitDate current) {
        return date != null ? date : current;
    }

    Memo memoOr(Memo current) {
        return memo != null ? memo : current;
    }

    PhotoRef photoOr(PhotoRef current) {
        return photoGiven ? photo : current;
    }
}
