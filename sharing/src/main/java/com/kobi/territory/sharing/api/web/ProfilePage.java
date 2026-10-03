package com.kobi.territory.sharing.api.web;

import com.kobi.territory.exploration.api.query.ProfileMapView;
import com.kobi.territory.sharing.application.PublicProfile;
import com.kobi.territory.sharing.domain.showcase.PublicVisit;
import com.kobi.territory.sharing.domain.showcase.RarityLabel;
import com.kobi.territory.sharing.domain.showcase.Showcase;
import com.kobi.territory.sharing.domain.showcase.YearRecap;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 공개 프로필 HTML(서버 렌더, 스크립트 없음 — 메신저 미리보기 봇도 그대로 읽는다). OG meta 의 og:image 는 영토 카드 PNG 의
 * 절대 주소다. 본문은 Showcase 가 허락한 것(색칠·집계·월 단위 시기)만 쓴다 — 메모·사진·정확한 날짜는 여기 올 수 없다.
 * "이 지도에 합류" 는 앱(/)으로 보내 앱이 자기 탐험가로 POST /maps/join-via-profile/{handle} 을 부르게 한다(초대코드 노출 없음).
 */
final class ProfilePage {

    private static final int RECENT_LIMIT = 8;

    private ProfilePage() {}

    static String render(PublicProfile profile, String baseUrl) {
        Showcase showcase = profile.showcase();
        String handle = showcase.handle();
        String path = "/u/" + urlPart(handle);
        String title = showcase.displayName() + "의 영토 · " + showcase.conquestPercent() + "%";
        String description = showcase.atlas().regionCount() + "곳 중 " + showcase.visits().count() + "곳 · 시·도 "
            + showcase.conqueredProvinces() + "곳 정복 · Lv." + showcase.progress().level();
        String image = baseUrl + path + "/card/territory.png";
        YearRecap recap = showcase.visits().recap(profile.year());
        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>").append(escape(title)).append("</title>")
            .append(meta("og:type", "profile"))
            .append(meta("og:site_name", "나의 영토"))
            .append(meta("og:title", title))
            .append(meta("og:description", description))
            .append(meta("og:url", baseUrl + path))
            .append(meta("og:image", image))
            .append(meta("og:image:width", "1200"))
            .append(meta("og:image:height", "630"))
            .append("<meta name=\"twitter:card\" content=\"summary_large_image\">")
            .append("<meta name=\"twitter:image\" content=\"").append(escape(image)).append("\">")
            .append("<meta name=\"description\" content=\"").append(escape(description)).append("\">")
            .append(STYLE).append("</head><body><main>");

        html.append("<header><h1 id=\"p-handle\">").append(escape(showcase.displayName())).append("</h1><p class=\"sub\">")
            .append(escape(showcase.progress().titleName() == null ? "" : showcase.progress().titleName()))
            .append(" · Lv.").append(showcase.progress().level()).append(" · 스트릭 ")
            .append(showcase.progress().streakMonths()).append("개월</p></header>");

        html.append("<section class=\"stats\" id=\"p-stats\">")
            .append(stat("정복률", showcase.conquestPercent() + "%", "conquest"))
            .append(stat("영토", showcase.visits().count() + " / " + showcase.atlas().regionCount(), "regions"))
            .append(stat("정복한 시·도", showcase.conqueredProvinces() + " / " + showcase.atlas().provinceCount(), "provinces"))
            .append(stat("전설 지역", showcase.visits().legendCount() + " / " + showcase.atlas().legendCount(), "legend"))
            .append(stat("도감 세트", showcase.progress().themesCompleted() + " / " + showcase.progress().themeTotal(), "sets"))
            .append(stat("꾸미기", showcase.scene().stylePoints() + "점", "style"))
            .append("</section>");

        html.append("<img class=\"card\" id=\"p-card\" src=\"").append(escape(path + "/card/territory.png"))
            .append("\" width=\"1200\" height=\"630\" alt=\"").append(escape(title)).append("\">");

        html.append("<section><h2>").append(recap.year()).append("년 새 영토 <b id=\"p-recap\">").append(recap.newRegions())
            .append("곳</b></h2>").append(recentList(showcase.visits().latest(RECENT_LIMIT))).append("</section>");

        html.append(mapsSection(handle, profile.joinableMaps()));

        html.append("<p class=\"note\">메모·사진은 공개되지 않아요. 날짜는 월 단위로만 보여요.</p>")
            .append("<p><a class=\"btn\" href=\"/\">나도 내 영토 칠하기</a></p>")
            .append("</main></body></html>");
        return html.toString();
    }

    static String notFound(String code) {
        return "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\"><title>찾을 수 없어요</title>" + STYLE
            + "</head><body><main><h1>찾을 수 없어요</h1><p class=\"sub\" data-code=\"" + escape(code)
            + "\">없는 프로필이거나 공개하지 않은 프로필이에요.</p><p><a class=\"btn\" href=\"/\">나의 영토로</a></p></main></body></html>";
    }

    private static String recentList(List<PublicVisit> visits) {
        if (visits.isEmpty()) return "<p class=\"sub\" id=\"p-recent-empty\">아직 칠한 곳이 없어요.</p>";
        StringBuilder list = new StringBuilder("<ol class=\"recent\" id=\"p-recent\">");
        visits.forEach(visit -> list.append("<li data-region=\"").append(escape(visit.region().code()))
            .append("\" data-month=\"").append(visit.month().iso()).append("\"><b>").append(escape(visit.region().name()))
            .append("</b> <span>").append(escape(visit.region().provinceName())).append(" · ")
            .append(RarityLabel.of(visit.region().rarity())).append("</span> <time>").append(visit.month().label())
            .append("</time></li>"));
        return list.append("</ol>").toString();
    }

    private static String mapsSection(String handle, List<ProfileMapView> maps) {
        if (maps.isEmpty()) return "";
        StringBuilder section = new StringBuilder("<section id=\"p-maps\"><h2>같이 칠하는 지도</h2><ul class=\"maps\">");
        maps.forEach(map -> {
            section.append("<li data-map=\"").append(escape(map.mapId())).append("\"><b>").append(escape(map.name()))
                .append("</b> <span>").append(map.memberCount()).append(" / ").append(map.maxMembers()).append("명</span> ");
            if (map.full()) {
                section.append("<span class=\"full\">가득 찼어요</span>");
            } else {
                section.append("<a class=\"btn primary\" data-join=\"").append(escape(map.mapId())).append("\" href=\"/?joinProfile=")
                    .append(urlPart(handle)).append("&amp;map=").append(urlPart(map.mapId())).append("\">이 지도에 합류</a>");
            }
            section.append("</li>");
        });
        return section.append("</ul></section>").toString();
    }

    private static String stat(String label, String value, String key) {
        return "<div data-stat=\"" + key + "\"><span>" + escape(label) + "</span><b>" + escape(value) + "</b></div>";
    }

    private static String meta(String property, String content) {
        return "<meta property=\"" + property + "\" content=\"" + escape(content) + "\">";
    }

    private static String urlPart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        if (value == null) return "";
        StringBuilder escaped = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            switch (character) {
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '&' -> escaped.append("&amp;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static final String STYLE = "<style>"
        + "body{margin:0;background:#f7f3ea;color:#22302e;font-family:'Noto Sans KR',system-ui,sans-serif}"
        + "main{max-width:760px;margin:0 auto;padding:24px 16px 48px}"
        + "h1{font-size:32px;margin:0}h2{font-size:18px;margin:24px 0 8px}.sub{color:#6b7c78;margin:4px 0 16px}"
        + ".stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}"
        + ".stats div{background:#fff;border-radius:10px;padding:10px 12px;display:flex;flex-direction:column}"
        + ".stats span{font-size:12px;color:#6b7c78}.stats b{font-size:22px}"
        + ".card{width:100%;height:auto;border-radius:12px;margin:16px 0;background:#10211f}"
        + ".recent,.maps{list-style:none;padding:0;margin:0;display:flex;flex-direction:column;gap:6px}"
        + ".recent li,.maps li{background:#fff;border-radius:8px;padding:8px 12px;display:flex;gap:8px;align-items:center;flex-wrap:wrap}"
        + ".recent span,.maps span{color:#6b7c78;font-size:13px}.recent time{margin-left:auto;font-size:13px;color:#2b8f80}"
        + ".btn{display:inline-block;border:1px solid #c9d6d2;border-radius:999px;padding:6px 14px;color:#22302e;text-decoration:none;background:#fff}"
        + ".btn.primary{background:#2fc3ad;border-color:#2fc3ad;color:#fff;margin-left:auto}.full{margin-left:auto}"
        + ".note{font-size:12px;color:#6b7c78;margin-top:24px}"
        + "@media(max-width:520px){.stats{grid-template-columns:repeat(2,1fr)}}"
        + "</style>";
}
