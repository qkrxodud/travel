package com.kobi.territory.sharing.infra.repository;

import com.kobi.territory.sharing.domain.card.CardImageStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * 카드 이미지 저장소 — local 파일시스템 어댑터(territory.share-card.storage-dir, build/ 밖). 운영은 이후 오브젝트 스토리지
 * 어댑터로 바꾼다(포트 {@link CardImageStorage}). 임시 파일에 쓰고 옮겨(원자적 교체) 읽는 쪽이 반쯤 쓴 파일을 보지 않게 한다.
 * 캐시라서 지워져도 된다 — 없으면 다시 그린다.
 */
@Repository
class LocalCardImageStorage implements CardImageStorage {

    private final Path root;

    LocalCardImageStorage(@Value("${territory.share-card.storage-dir}") String storageDir) {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(String key, byte[] png) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "card-", ".tmp");
            Files.write(temp, png);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return key;
        } catch (IOException failure) {
            throw new UncheckedIOException("카드 이미지 저장 실패: " + target, failure);
        }
    }

    @Override
    public Optional<byte[]> load(String key) {
        Path target = resolve(key);
        try {
            return Files.isRegularFile(target) ? Optional.of(Files.readAllBytes(target)) : Optional.empty();
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // 캐시 파일 — 못 지워도 동작에 지장 없다(다음 렌더가 다른 키로 쓴다)
        }
    }

    /** 키가 저장 경로 밖을 가리키지 못하게 한다(키는 서버가 만들지만 방어). */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("저장 경로 밖 키: " + key);
        return target;
    }
}
