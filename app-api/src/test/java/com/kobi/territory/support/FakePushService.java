package com.kobi.territory.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 시험용 가짜 브라우저 푸시 서비스(FCM 대신) — 127.0.0.1 의 임의 포트에서 구독 주소로 받은 알림을 상자(경로)마다 모은다. 상자 이름이
 * {@code gone} 으로 시작하면 410, {@code busy} 면 429(Retry-After 1), {@code reject} 면 403, 아니면 201. 브라우저처럼 키 쌍을 만들어 두고 받은
 * 암호문을 풀어 볼 수 있다(RFC 8291). local 프로파일은 localhost 구독 주소를 받는다(territory.push.allow-localhost).
 */
public final class FakePushService implements AutoCloseable {

    /** P-256 공개 키 X.509 머리(SubjectPublicKeyInfo) — 뒤에 비압축 점 65바이트가 붙는다. */
    private static final byte[] X509_P256_PREFIX = Base64.getDecoder().decode("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgA=");

    private final HttpServer server;
    private final Map<String, List<Received>> boxes = new ConcurrentHashMap<>();
    private final Map<String, Integer> forcedStatus = new ConcurrentHashMap<>();

    /** 받은 알림 한 건. */
    public record Received(Map<String, String> headers, byte[] body) {}

    /** 구독한 브라우저 하나 — 구독 주소·키와 그 개인 키. */
    public record Browser(String endpoint, String p256dh, String auth, KeyPair keys, byte[] authBytes, byte[] publicPoint) {
        public String subscriptionJson() {
            return "{\"endpoint\":\"" + endpoint + "\",\"expirationTime\":null,\"keys\":{\"p256dh\":\"" + p256dh + "\",\"auth\":\"" + auth
                + "\"}}";
        }
    }

    public FakePushService() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/push/", exchange -> {
            String box = exchange.getRequestURI().getPath().substring("/push/".length());
            Map<String, String> headers = new ConcurrentHashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), String.join(",", values)));
            boxes.computeIfAbsent(box, name -> new CopyOnWriteArrayList<>()).add(new Received(headers, exchange.getRequestBody().readAllBytes()));
            int status = forcedStatus.getOrDefault(box, box.startsWith("gone") ? 410 : box.startsWith("busy") ? 429
                : box.startsWith("reject") ? 403 : 201);
            if (status == 429) exchange.getResponseHeaders().add("Retry-After", "1");
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    /** 이 상자 이름으로 구독하는 새 브라우저. */
    public Browser browser(String box) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keys = generator.generateKeyPair();
            byte[] encoded = keys.getPublic().getEncoded();
            byte[] point = Arrays.copyOfRange(encoded, encoded.length - 65, encoded.length);
            byte[] auth = new byte[16];
            new SecureRandom().nextBytes(auth);
            Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
            return new Browser("http://127.0.0.1:" + server.getAddress().getPort() + "/push/" + box, url.encodeToString(point),
                url.encodeToString(auth), keys, auth, point);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    public List<Received> received(String box) {
        return boxes.getOrDefault(box, List.of());
    }

    /** 이 상자의 응답을 바꾼다(예: 처음엔 429, 나중엔 201). */
    public void respond(String box, int status) {
        forcedStatus.put(box, status);
    }

    public void clear() {
        boxes.clear();
        forcedStatus.clear();
    }

    /** 브라우저처럼 받은 알림을 풀어 본다(RFC 8291). */
    public static String decrypt(Received received, Browser browser) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(received.body());
            byte[] salt = new byte[16];
            buffer.get(salt);
            buffer.getInt();
            byte[] serverPoint = new byte[buffer.get()];
            buffer.get(serverPoint);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            byte[] spki = ByteBuffer.allocate(X509_P256_PREFIX.length + 65).put(X509_P256_PREFIX).put(serverPoint).array();
            PublicKey serverKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki));
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(browser.keys().getPrivate());
            agreement.doPhase(serverKey, true);
            byte[] prkKey = hmac(browser.authBytes(), agreement.generateSecret());
            byte[] ikm = hmac(prkKey, concat(ascii("WebPush: info\0"), browser.publicPoint(), serverPoint, new byte[] {1}));
            byte[] prk = hmac(salt, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, concat(ascii("Content-Encoding: aes128gcm\0"), new byte[] {1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, concat(ascii("Content-Encoding: nonce\0"), new byte[] {1})), 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] padded = cipher.doFinal(ciphertext);
            int end = padded.length - 1;
            while (padded[end] == 0) end--;
            return new String(padded, 0, end, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException failed) {
            throw new IllegalStateException("알림을 풀 수 없다", failed);
        }
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteBuffer joined = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(part -> part.length).sum());
        Arrays.stream(parts).forEach(joined::put);
        return joined.array();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
