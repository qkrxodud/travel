package com.kobi.territory.sharing.domain.showcase;

import java.util.List;

/**
 * 자랑 카드 한 장의 내용(렌더러 입력). 프로토타입 카드 4종(territoryCard·recentCard·recapCard·vsCard)의 정보 구성을 따르되
 * 메모·사진·정확한 날짜는 없다(공개 카드 — 날짜는 월 단위).
 */
public sealed interface CardContent {

    /** 카드 아래 공개 프로필 경로(/u/{handle}). */
    String footer();

    MapPaint paint();

    /** 영토 카드: 정복률·지역 수·시·도 정복·전설·도감·레벨·꾸미기. */
    record Territory(String footer, MapPaint paint, String headline, int conquestPercent, int visited, int total,
                     List<Stat> stats, List<String> wornItemNames) implements CardContent {
        public Territory {
            stats = List.copyOf(stats);
            wornItemNames = List.copyOf(wornItemNames);
        }
    }

    /** 최근 여행 카드: 가장 최근 방문 지역(월 단위). 방문이 없으면 regionName 이 null. */
    record Recent(String footer, MapPaint paint, String headline, String regionName, String detail, boolean legend,
                  List<Stat> stats) implements CardContent {
        public Recent {
            stats = List.copyOf(stats);
        }
    }

    /** 연간 리캡 카드. */
    record Recap(String footer, MapPaint paint, String headline, int newRegions, String subline, List<Integer> monthCounts,
                 List<Stat> stats) implements CardContent {
        public Recap {
            monthCounts = List.copyOf(monthCounts);
            stats = List.copyOf(stats);
        }
    }

    /** VS 카드: 두 탐험가 영토 비교. */
    record Versus(String footer, MapPaint paint, String myName, String theirName, VersusTally tally) implements CardContent {}

    /** 카드 오른쪽의 이름-값 한 줄. */
    record Stat(String label, String value) {}
}
