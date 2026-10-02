package com.kobi.territory.api;

/**
 * 표준 에러 응답. code 는 분기용 안정 문자열(예: DAILY_CAP_EXCEEDED), message 는 사용자에게 그대로 보여줄 한국어 문장.
 */
public record ErrorResponse(String code, String message) {}
