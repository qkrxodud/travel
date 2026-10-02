package com.kobi.territory.common.error;

/**
 * 모든 컨텍스트가 공유하는 비즈니스 예외. {@code code}는 클라이언트가 분기에 쓰는 안정적인 문자열이고
 * {@code message}는 사용자에게 그대로 보여줄 수 있는 한국어 문장이다.
 */
public class TerritoryException extends RuntimeException {

    private final String code;
    private final ErrorKind kind;

    public TerritoryException(String code, ErrorKind kind, String message) {
        super(message);
        this.code = code;
        this.kind = kind;
    }

    public String code() {
        return code;
    }

    public ErrorKind kind() {
        return kind;
    }
}
