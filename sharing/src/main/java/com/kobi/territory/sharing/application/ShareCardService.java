package com.kobi.territory.sharing.application;

import com.kobi.territory.common.model.ExplorerId;
import com.kobi.territory.exploration.api.query.ExplorerProfileQuery;
import com.kobi.territory.exploration.api.query.TerritoryQuery;
import com.kobi.territory.sharing.domain.SharingError;
import com.kobi.territory.sharing.domain.card.CardBasis;
import com.kobi.territory.sharing.domain.card.CardCachePolicy;
import com.kobi.territory.sharing.domain.card.CardImageStorage;
import com.kobi.territory.sharing.domain.card.CardKind;
import com.kobi.territory.sharing.domain.card.ShareCard;
import com.kobi.territory.sharing.domain.card.ShareCardId;
import com.kobi.territory.sharing.domain.card.ShareCardRepository;
import com.kobi.territory.sharing.domain.showcase.CardComposer;
import com.kobi.territory.sharing.domain.showcase.CardContent;
import com.kobi.territory.sharing.domain.showcase.CardRenderer;
import com.kobi.territory.sharing.domain.showcase.Showcase;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 자랑 카드(ShareCard) 유스케이스 — 링크가 열릴 때 그린다(lazy, §2-8). 기준은 지금 실제로 그릴 공개 요약(Showcase)의 해시 +
 * handle(QA P2-1·P2-2): 요청마다 요약을 읽어(가벼운 Query) 해시를 내고, 다시 그릴지는 ShareCard 가 판단한다(handle 이 바뀌면 TTL
 * 면제, 요약이 바뀌면 최소 TTL 뒤). 렌더(PNG)·이미지 저장은 트랜잭션 밖, 기록만 짧은 트랜잭션. 이미지 키에 기준 해시가 들어가므로
 * 동시 렌더가 엇갈려도 "DB 기준 ≠ 저장 이미지"가 되지 않는다(QA P3-4) — 기록에 진 쪽은 자기 파일을 지운다.
 * <p>
 * 공개 경로는 공개 범위가 PUBLIC 일 때만(아니면 PROFILE_NOT_FOUND — 존재 숨김). 내 카드 미리보기는 공개 범위와 무관하다.
 * VS 카드는 저장하지 않고 메모리 캐시만(QA P3-9).
 */
@Service
public class ShareCardService {

    private final ShareCardRepository cards;
    private final CardImageStorage images;
    private final CardRenderer renderer;
    private final ShowcaseReader showcases;
    private final PrivacyService privacy;
    private final ExplorerProfileQuery profiles;
    private final TerritoryQuery territories;
    private final CardCachePolicy cachePolicy;
    private final VersusCardCache versusCache;
    private final Clock clock;
    private final TransactionTemplate writeTx;

    public ShareCardService(ShareCardRepository cards, CardImageStorage images, CardRenderer renderer, ShowcaseReader showcases,
                            PrivacyService privacy, ExplorerProfileQuery profiles, TerritoryQuery territories,
                            SharingSettings settings, Clock clock, PlatformTransactionManager transactionManager) {
        this.cards = cards;
        this.images = images;
        this.renderer = renderer;
        this.showcases = showcases;
        this.privacy = privacy;
        this.profiles = profiles;
        this.territories = territories;
        this.cachePolicy = settings.cachePolicy();
        this.versusCache = new VersusCardCache(settings.cardMinTtl());
        this.clock = clock;
        this.writeTx = new TransactionTemplate(transactionManager);
    }

    /** GET /u/{handle}/card/{kind}.png — 공개 프로필 주인의 카드. */
    public CardImage publicCard(String handle, String kind) {
        CardKind cardKind = CardKind.parseSolo(kind);
        ExplorerId owner = publicOwner(handle);
        return soloCard(owner, profiles.handleOf(owner.value()).orElse(null), cardKind);
    }

    /** GET /u/{handle}/vs/{otherHandle}.png — 두 탐험가가 모두 공개일 때만. 자기 자신과의 비교는 없다(404). */
    public CardImage publicVersus(String handle, String otherHandle) {
        ExplorerId owner = publicOwner(handle);
        ExplorerId other = publicOwner(otherHandle);
        Showcase mine = showcases.read(owner.value(), profiles.handleOf(owner.value()).orElse(null));
        Showcase theirs = showcases.read(other.value(), profiles.handleOf(other.value()).orElse(null));
        String key = mine.pairHash(theirs, CardKind.VS.name() + "@" + Year.now(clock));
        Instant now = clock.instant();
        return versusCache.find(key, now).orElseGet(() -> {
            CardImage image = new CardImage(renderer.render(CardComposer.versus(mine, theirs)), now);
            versusCache.put(key, image);
            return image;
        });
    }

    /** GET /me/cards/{kind}.png — 내 카드 미리보기(익명이면 handle 없이). 탐험가가 없으면 404 EXPLORER_NOT_FOUND. */
    public CardImage myCard(ExplorerId explorerId, String kind) {
        CardKind cardKind = CardKind.parseSolo(kind);
        territories.personalMapId(explorerId.value());
        return soloCard(explorerId, profiles.handleOf(explorerId.value()).orElse(null), cardKind);
    }

    /** GET /me/cards — 내 카드 3종(영토·최근·리캡)의 렌더 상태(지금 요약 해시 기준 stale). */
    public List<CardMeta> myCards(ExplorerId explorerId) {
        String mapId = territories.personalMapId(explorerId.value());
        Showcase showcase = showcases.read(explorerId.value(), profiles.handleOf(explorerId.value()).orElse(null));
        Year year = Year.now(clock);
        return CardKind.SOLO.stream().map(kind -> {
            ShareCardId id = new ShareCardId(explorerId, mapId, kind);
            ShareCard card = cards.find(id).orElseGet(() -> ShareCard.unrendered(id));
            return new CardMeta(kind, card.rendered(), card.stale(basisOf(showcase, kind, year)), card.renderedAt());
        }).toList();
    }

    private CardImage soloCard(ExplorerId owner, String handle, CardKind kind) {
        ShareCardId id = new ShareCardId(owner, territories.personalMapId(owner.value()), kind);
        Year year = Year.now(clock);
        Showcase showcase = showcases.read(owner.value(), handle);
        CardBasis basis = basisOf(showcase, kind, year);
        Instant now = clock.instant();
        ShareCard card = cards.find(id).orElseGet(() -> ShareCard.unrendered(id));
        Optional<byte[]> cached = card.needsRender(basis, now, cachePolicy) ? Optional.empty() : images.load(card.imageKey());
        return cached.map(png -> new CardImage(png, card.renderedAt()))
            .orElseGet(() -> renderAndRecord(id, basis, CardComposer.compose(kind, showcase, year), now));
    }

    private static CardBasis basisOf(Showcase showcase, CardKind kind, Year year) {
        return new CardBasis(showcase.summaryHash(kind.name() + "@" + year), showcase.handle());
    }

    private ExplorerId publicOwner(String handle) {
        ExplorerId owner = profiles.explorerIdByHandle(handle).map(ExplorerId::of)
            .orElseThrow(SharingError.PROFILE_NOT_FOUND::exception);
        privacy.requireVisibleToPublic(owner);
        return owner;
    }

    private CardImage renderAndRecord(ShareCardId id, CardBasis basis, CardContent content,
                                      Instant now) {
        byte[] png = renderer.render(content);
        String key = images.store(id.imageKey(basis), png);
        AtomicReference<String> replaced = new AtomicReference<>();
        try {
            writeTx.executeWithoutResult(status -> {
                ShareCard card = cards.find(id).orElseGet(() -> ShareCard.unrendered(id));
                replaced.set(card.markRendered(basis, key, now));
                cards.save(card);
            });
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException concurrentRender) {
            // 같은 카드를 동시에 그린 다른 요청이 먼저 기록했다 — 그 기록을 두고, 그 기록이 가리키지 않는 내 파일은 지운다.
            cards.find(id).filter(winner -> !key.equals(winner.imageKey())).ifPresent(winner -> images.delete(key));
            return new CardImage(png, now);
        }
        Optional.ofNullable(replaced.get()).ifPresent(images::delete);
        return new CardImage(png, now);
    }
}
