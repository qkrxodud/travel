package com.kobi.territory.social.domain.ranking;

import com.kobi.territory.common.model.ExplorerId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 일급 컬렉션: 친구 랭킹(§5 — 요청 시점 조인 계산, 나 + 서로 팔로우한 친구). 순서: 지역 수 내림차순 → 레벨 내림차순 → 본인 먼저 →
 * id 순. 순위는 지역 수만으로 매긴다(같은 지역 수 = 같은 순위). 친구가 없으면 콜드 스타트 비교(내 지역 평균 유저)가 필요하다.
 */
public final class FriendLeaderboard {

    private final ExplorerId me;
    private final List<FriendStanding> standings;

    private FriendLeaderboard(ExplorerId me, List<FriendStanding> standings) {
        this.me = me;
        this.standings = List.copyOf(standings);
    }

    /** @param scores 나 + 친구의 점수(본인 점수가 없으면 0곳·Lv.1 로 넣는다) */
    public static FriendLeaderboard of(ExplorerId me, Collection<FriendScore> scores) {
        Objects.requireNonNull(me, "me");
        List<FriendScore> everyone = new ArrayList<>(scores.stream().filter(score -> !score.explorerId().equals(me)).toList());
        everyone.add(scores.stream().filter(score -> score.explorerId().equals(me)).findFirst()
            .orElseGet(() -> new FriendScore(me, 0, 1)));
        List<FriendScore> ordered = everyone.stream()
            .sorted(Comparator.comparingInt(FriendScore::regionCount).reversed()
                .thenComparing(Comparator.comparingInt(FriendScore::level).reversed())
                .thenComparing(score -> !score.explorerId().equals(me))
                .thenComparing(score -> score.explorerId().value()))
            .toList();
        List<FriendStanding> standings = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            FriendScore score = ordered.get(i);
            boolean tied = i > 0 && ordered.get(i - 1).regionCount() == score.regionCount();
            int rank = tied ? standings.get(i - 1).rank() : i + 1;
            standings.add(new FriendStanding(score.explorerId(), score.regionCount(), score.level(), rank, score.explorerId().equals(me)));
        }
        return new FriendLeaderboard(me, standings);
    }

    public List<FriendStanding> standings() {
        return standings;
    }

    /** 친구(본인 제외) 수. */
    public int friendCount() {
        return standings.size() - 1;
    }

    /** 친구가 없어 "내 지역 평균 유저"와 비교해야 하는지(콜드 스타트, §7). */
    public boolean needsBaseline() {
        return friendCount() == 0;
    }

    public FriendStanding mine() {
        return standings.stream().filter(FriendStanding::me).findFirst().orElseThrow();
    }

    @Override
    public String toString() {
        return "FriendLeaderboard[" + me + ", " + standings + "]";
    }
}
