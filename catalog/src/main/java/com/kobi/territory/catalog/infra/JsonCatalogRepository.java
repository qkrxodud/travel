package com.kobi.territory.catalog.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kobi.territory.catalog.domain.Catalog;
import com.kobi.territory.catalog.domain.CatalogRepository;
import com.kobi.territory.catalog.domain.ItemDefinition;
import com.kobi.territory.catalog.domain.ItemDefinitions;
import com.kobi.territory.catalog.domain.ItemSlot;
import com.kobi.territory.catalog.domain.Province;
import com.kobi.territory.catalog.domain.Provinces;
import com.kobi.territory.catalog.domain.Region;
import com.kobi.territory.catalog.domain.Regions;
import com.kobi.territory.catalog.domain.RewardRules;
import com.kobi.territory.common.model.Rarity;
import com.kobi.territory.common.model.RegionCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * classpath:catalog/*.json 을 시작 시 한 번 읽어 메모리에 올린다.
 * 데이터 원본: 프로토타입 doc/나의 영토.html → tools/catalog/gen-catalog.js 로 생성.
 * 파싱만 하고, 정합성 검증은 도메인 Catalog 생성자가 한다(어긋나면 기동 실패).
 */
@Repository
public class JsonCatalogRepository implements CatalogRepository {

    private static final String BASE = "catalog/";

    private final Catalog catalog;

    public JsonCatalogRepository() {
        ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<Province> provinces = read(om, "provinces.json", new TypeReference<List<ProvinceJson>>() {}).stream()
            .map(p -> new Province(p.code, p.name, p.fullName, p.displayOrder, p.regionCount)).toList();
        List<Region> regions = read(om, "regions.json", new TypeReference<List<RegionJson>>() {}).stream()
            .map(r -> new Region(RegionCode.of(r.code), r.name, r.provinceCode, Rarity.valueOf(r.rarity), r.countryCode,
                r.version, r.replacedBy == null ? null : RegionCode.of(r.replacedBy),
                r.retiredAt == null ? null : LocalDate.parse(r.retiredAt)))
            .toList();
        List<ItemDefinition> items = read(om, "items.json", new TypeReference<List<ItemJson>>() {}).stream()
            .map(i -> new ItemDefinition(i.itemId, i.regionCode == null ? null : RegionCode.of(i.regionCode), i.name, i.emoji,
                ItemSlot.valueOf(i.slot), Rarity.valueOf(i.tier), i.theme,
                i.look == null ? null : new ItemDefinition.Look(i.look.type, i.look.primary, i.look.secondary)))
            .toList();
        RewardJson rj = read(om, "reward-rules.json", new TypeReference<RewardJson>() {});
        Map<Rarity, Integer> xp = new HashMap<>();
        rj.xpByRarity.forEach((k, v) -> xp.put(Rarity.valueOf(k), v));
        // 정합성 검증은 도메인(Catalog·일급 컬렉션)이 생성 시 한다
        this.catalog = new Catalog(Regions.of(regions), Provinces.of(provinces), ItemDefinitions.of(items),
            new RewardRules(xp, rj.provinceFirstBonus, rj.setCompleteBonus, rj.claimBonus), readString("regions.geojson"));
    }

    @Override
    public Catalog load() {
        return catalog;
    }

    private static <T> T read(ObjectMapper om, String name, TypeReference<T> type) {
        try (InputStream in = open(name)) {
            return om.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("카탈로그 로딩 실패: " + name, e);
        }
    }

    private static String readString(String name) {
        try (InputStream in = open(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("카탈로그 로딩 실패: " + name, e);
        }
    }

    private static InputStream open(String name) throws IOException {
        InputStream in = JsonCatalogRepository.class.getClassLoader().getResourceAsStream(BASE + name);
        if (in == null) throw new IOException("classpath:" + BASE + name + " 없음");
        return in;
    }

    record ProvinceJson(String code, String name, String fullName, int displayOrder, int regionCount) {}

    record RegionJson(String code, String name, String provinceCode, String rarity, String countryCode, int version,
                      String replacedBy, String retiredAt) {}

    record LookJson(String type, String primary, String secondary) {}

    record ItemJson(String itemId, String regionCode, String name, String emoji, String slot, String tier, String theme,
                    LookJson look) {}

    record RewardJson(Map<String, Integer> xpByRarity, int provinceFirstBonus, int setCompleteBonus, int claimBonus) {}
}
