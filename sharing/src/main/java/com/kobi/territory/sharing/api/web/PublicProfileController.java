package com.kobi.territory.sharing.api.web;

import com.kobi.territory.common.error.ErrorKind;
import com.kobi.territory.common.error.TerritoryException;
import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.sharing.application.CardImage;
import com.kobi.territory.sharing.application.PublicProfileService;
import com.kobi.territory.sharing.application.ShareCardService;
import com.kobi.territory.sharing.application.SharingSettings;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 프로필(인증 없음 — 5단계부터 보는 사람을 @CurrentExplorer(required = false)로 선택적으로 식별한다: 로그인 세션·토큰이 있으면
 * 그 탐험가, 없거나 풀리지 않으면 익명 방문자). 공개 범위 PUBLIC 은 누구나, FRIENDS 는 맞팔로우한 친구만, 아니면 404(존재 숨김). 보여 주는 것은 색칠·집계·
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
    private static final CacheControl PRIVATE_IMAGE_CACHE = CacheControl.maxAge(Duration.ofMinutes(1)).cachePrivate();

    private final PublicProfileService profiles;
    private final ShareCardService cards;
    private final String publicBaseUrl;

    public PublicProfileController(PublicProfileService profiles, ShareCardService cards, SharingSettings settings) {
        this.profiles = profiles;
        this.cards = cards;
        this.publicBaseUrl = settings.publicBaseUrl();
    }

    @GetMapping("/u/{handle}")
    public ResponseEntity<String> profile(@PathVariable("handle") String handle,
                                          @CurrentExplorer(required = false) ExplorerId viewer) {
        // 절대 주소는 설정값(territory.public-base-url) — 요청 Host 헤더를 믿지 않는다(QA P3-2)
        // 보는 사람에 따라 응답이 달라진다(FRIENDS·주인 미리보기) — 로그인 응답은 private, 쿠키별로 캐시를 가른다(QA P3-4)
        return ResponseEntity.ok().contentType(HTML)
            .cacheControl(viewer == null ? CacheControl.noCache() : CacheControl.noCache().cachePrivate())
            .varyBy(HttpHeaders.COOKIE)
            .body(ProfilePage.render(profiles.profile(handle, viewer), publicBaseUrl));
    }

    @GetMapping("/u/{handle}/card/{kind}.png")
    public ResponseEntity<byte[]> card(@PathVariable("handle") String handle, @PathVariable("kind") String kind,
                                       @CurrentExplorer(required = false) ExplorerId viewer) {
        return png(cards.publicCard(handle, kind, viewer), viewer);
    }

    @GetMapping("/u/{handle}/vs/{otherHandle}.png")
    public ResponseEntity<byte[]> versus(@PathVariable("handle") String handle, @PathVariable("otherHandle") String otherHandle,
                                         @CurrentExplorer(required = false) ExplorerId viewer) {
        return png(cards.publicVersus(handle, otherHandle, viewer), viewer);
    }

    /**
     * 공개 PNG. 로그인한 사람이 본 응답(FRIENDS 카드일 수 있음)은 공용 캐시(메신저·프록시)에 남기지 않는다(private) — 공개 범위를
     * 친구로 바꾼 뒤에도 공용 캐시가 낯선 사람에게 내주지 않게(5단계).
     */
    private static ResponseEntity<byte[]> png(CardImage image, ExplorerId viewer) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
            .cacheControl(viewer == null ? PUBLIC_IMAGE_CACHE : PRIVATE_IMAGE_CACHE).varyBy(HttpHeaders.COOKIE)
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
