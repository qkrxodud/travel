package com.kobi.territory.notification.infra.webpush;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

/**
 * P-256(secp256r1) 키 변환 — 웹 푸시가 쓰는 형식(공개 키 = 비압축 점 65바이트 {@code 0x04‖X‖Y}, 개인 키 = 스칼라 32바이트)과 JDK 키
 * 객체 사이. JDK 표준 암호(SunEC)만 쓴다(BouncyCastle 없음). 받은 공개 키는 곡선 위의 점인지 직접 확인한다(잘못된 점 공격 방지).
 */
final class P256 {

    static final ECParameterSpec PARAMS = parameters();
    private static final int FIELD_BYTES = 32;

    private P256() {}

    private static ECParameterSpec parameters() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("이 JDK 에 P-256 이 없다", unavailable);
        }
    }

    /** 비압축 점 65바이트 → 공개 키. 형식이 틀리거나 곡선 위의 점이 아니면 IllegalArgumentException. */
    static ECPublicKey publicKey(byte[] uncompressed) {
        if (uncompressed == null || uncompressed.length != 1 + 2 * FIELD_BYTES || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("P-256 공개 키는 0x04 로 시작하는 65바이트여야 한다");
        }
        BigInteger affineX = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 1 + FIELD_BYTES));
        BigInteger affineY = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1 + FIELD_BYTES, uncompressed.length));
        if (!onCurve(affineX, affineY)) throw new IllegalArgumentException("P-256 곡선 위의 점이 아니다");
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(affineX, affineY), PARAMS));
        } catch (GeneralSecurityException invalid) {
            throw new IllegalArgumentException("P-256 공개 키를 만들 수 없다", invalid);
        }
    }

    /** 스칼라 32바이트 → 개인 키. 범위(1 ~ n−1) 밖이면 IllegalArgumentException. */
    static ECPrivateKey privateKey(byte[] scalar) {
        if (scalar == null || scalar.length != FIELD_BYTES) throw new IllegalArgumentException("P-256 개인 키는 32바이트여야 한다");
        BigInteger value = new BigInteger(1, scalar);
        if (value.signum() == 0 || value.compareTo(PARAMS.getOrder()) >= 0) {
            throw new IllegalArgumentException("P-256 개인 키가 범위 밖이다");
        }
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(value, PARAMS));
        } catch (GeneralSecurityException invalid) {
            throw new IllegalArgumentException("P-256 개인 키를 만들 수 없다", invalid);
        }
    }

    /** 공개 키 → 비압축 점 65바이트. */
    static byte[] encode(ECPublicKey key) {
        byte[] encoded = new byte[1 + 2 * FIELD_BYTES];
        encoded[0] = 0x04;
        copyFixed(key.getW().getAffineX(), encoded, 1);
        copyFixed(key.getW().getAffineY(), encoded, 1 + FIELD_BYTES);
        return encoded;
    }

    private static void copyFixed(BigInteger value, byte[] target, int offset) {
        byte[] raw = value.toByteArray();
        int length = Math.min(raw.length, FIELD_BYTES);
        System.arraycopy(raw, raw.length - length, target, offset + FIELD_BYTES - length, length);
    }

    /** y² ≡ x³ + ax + b (mod p). */
    private static boolean onCurve(BigInteger affineX, BigInteger affineY) {
        BigInteger prime = ((ECFieldFp) PARAMS.getCurve().getField()).getP();
        if (affineX.compareTo(prime) >= 0 || affineY.compareTo(prime) >= 0) return false;
        BigInteger left = affineY.multiply(affineY).mod(prime);
        BigInteger right = affineX.pow(3).add(PARAMS.getCurve().getA().multiply(affineX)).add(PARAMS.getCurve().getB()).mod(prime);
        return left.equals(right);
    }
}
