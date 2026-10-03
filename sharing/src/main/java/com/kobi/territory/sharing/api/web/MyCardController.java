package com.kobi.territory.sharing.api.web;

import com.kobi.territory.common.identity.CurrentExplorer;
import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.sharing.api.web.SharingDtos.CardMetaResponse;
import com.kobi.territory.sharing.api.web.SharingDtos.MyCardsResponse;
import com.kobi.territory.sharing.api.web.SharingDtos.PrivacyRequest;
import com.kobi.territory.sharing.api.web.SharingDtos.PrivacyResponse;
import com.kobi.territory.sharing.application.CardImage;
import com.kobi.territory.sharing.application.PrivacyService;
import com.kobi.territory.sharing.application.ShareCardService;
import com.kobi.territory.sharing.domain.privacy.PrivacySettings;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 내 카드(미리보기·메타)와 공개 범위. 인증 = @CurrentExplorer(로그인 세션 → 토큰). */
@RestController
public class MyCardController {

    private final ShareCardService cards;
    private final PrivacyService privacy;
    private final ExplorerProfileQuery profiles;

    public MyCardController(ShareCardService cards, PrivacyService privacy, ExplorerProfileQuery profiles) {
        this.cards = cards;
        this.privacy = privacy;
        this.profiles = profiles;
    }

    @GetMapping("/me/cards")
    public MyCardsResponse myCards(@CurrentExplorer ExplorerId explorerId) {
        String handle = profiles.handleOf(explorerId.value()).orElse(null);
        PrivacySettings settings = privacy.view(explorerId);
        return new MyCardsResponse(handle, handle == null ? null : "/u/" + handle, settings.visibility().name(),
            settings.visibleToPublic(),
            cards.myCards(explorerId).stream().map(meta -> new CardMetaResponse(meta.kind().name(),
                "/me/cards/" + meta.kind().pathValue() + ".png",
                handle == null ? null : "/u/" + handle + "/card/" + meta.kind().pathValue() + ".png",
                meta.rendered(), meta.stale(), meta.renderedAt())).toList());
    }

    /** 내 카드 미리보기 PNG(공개 범위와 무관 — 내 것). */
    @GetMapping("/me/cards/{kind}.png")
    public ResponseEntity<byte[]> myCard(@CurrentExplorer ExplorerId explorerId, @PathVariable("kind") String kind) {
        CardImage image = cards.myCard(explorerId, kind);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(image.png());
    }

    @GetMapping("/me/privacy")
    public PrivacyResponse privacy(@CurrentExplorer ExplorerId explorerId) {
        return toResponse(privacy.view(explorerId));
    }

    @PutMapping("/me/privacy")
    public PrivacyResponse changePrivacy(@CurrentExplorer ExplorerId explorerId, @RequestBody PrivacyRequest request) {
        return toResponse(privacy.change(explorerId, request.visibility()));
    }

    private static PrivacyResponse toResponse(PrivacySettings settings) {
        return new PrivacyResponse(settings.visibility().name(), settings.visibleToPublic(), settings.updatedAt());
    }
}
