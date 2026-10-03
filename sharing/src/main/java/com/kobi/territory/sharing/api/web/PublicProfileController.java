package com.kobi.territory.sharing.api.web;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.sharing.application.CardImage;
import com.kobi.territory.sharing.application.PublicProfileService;
import com.kobi.territory.sharing.application.ShareCardService;
import com.kobi.territory.sharing.application.SharingSettings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 프로필(인증 없음 — @CurrentExplorer 를 쓰지 않는다). 공개 범위 PUBLIC 만, 아니면 404(존재 숨김). 보여 주는 것은 색칠·집계·
 * 월 단위 시기뿐(메모·사진·정확한 날짜 없음, §7).
 * <ul>
 *   <li>{@code GET /u/{handle}} — 서버 렌더 HTML + OG meta(og:image = 영토 카드 PNG)</li>
 *   <li>{@code GET /u/{handle}/card/{kind}.png} — territory·recent·recap 카드(lazy 렌더·캐시)</li>
 *   <li>{@code GET /u/{handle}/vs/{otherHandle}.png} — VS 카드(둘 다 공개일 때만)</li>
 * </ul>
 */
@RestController
public class PublicProfileController {

    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
    /** 공개 PNG 브라우저·메신저 캐시 — 서버 쪽 최소 TTL 과 별개로 짧게(바뀐 카드가 곧 보이게). */
    private static final CacheControl PUBLIC_IMAGE_CACHE = CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic();

    private final PublicProfileService profiles;
    private final ShareCardService cards;
    private final String publicBaseUrl;

    public PublicProfileController(PublicProfileService profiles, ShareCardService cards, SharingSettings settings) {
        this.profiles = profiles;
        this.cards = cards;
        this.publicBaseUrl = settings.publicBaseUrl();
    }

    @GetMapping("/u/{handle}")
    public ResponseEntity<String> profile(@PathVariable("handle") String handle) {
        // 절대 주소는 설정값(territory.public-base-url) — 요청 Host 헤더를 믿지 않는다(QA P3-2)
        return ResponseEntity.ok().contentType(HTML).cacheControl(CacheControl.noCache())
            .body(ProfilePage.render(profiles.profile(handle), publicBaseUrl));
    }

    @GetMapping("/u/{handle}/card/{kind}.png")
    public ResponseEntity<byte[]> card(@PathVariable("handle") String handle, @PathVariable("kind") String kind) {
        return png(cards.publicCard(handle, kind));
    }

    @GetMapping("/u/{handle}/vs/{otherHandle}.png")
    public ResponseEntity<byte[]> versus(@PathVariable("handle") String handle, @PathVariable("otherHandle") String otherHandle) {
        return png(cards.publicVersus(handle, otherHandle));
    }

    private static ResponseEntity<byte[]> png(CardImage image) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(PUBLIC_IMAGE_CACHE)
            .lastModified(image.renderedAt()).body(image.png());
    }

    /** 공개 경로는 사람이 브라우저로 여는 곳이라 오류도 짧은 HTML 로(없음 = 404, 존재 여부를 말하지 않는다). */
    @ExceptionHandler(TerritoryException.class)
    public ResponseEntity<String> notAvailable(TerritoryException exception) {
        HttpStatus status = exception.kind() == ErrorKind.NOT_FOUND ? HttpStatus.NOT_FOUND
            : exception.kind() == ErrorKind.INVALID ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).contentType(HTML).cacheControl(CacheControl.noStore())
            .body(ProfilePage.notFound(exception.code()));
    }
}
