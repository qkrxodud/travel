package com.kobi.territory.exploration.domain;

/** 사진 참조(URL). 업로드는 이후 단계 — 지금은 문자열 참조만 보관한다. */
public record PhotoRef(String url) {

    public static final int MAX_LENGTH = 500;

    public PhotoRef {
        if (url == null || url.isBlank() || url.length() > MAX_LENGTH) {
            throw ExplorationError.INVALID_PHOTO_REF.exception();
        }
        url = url.strip();
    }

    public static PhotoRef ofNullable(String url) {
        return url == null || url.isBlank() ? null : new PhotoRef(url);
    }
}
