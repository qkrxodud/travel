package com.kobi.territory.catalog.api.web;

import com.kobi.territory.catalog.api.query.ItemView;
import com.kobi.territory.catalog.application.ItemCatalogService;
import com.kobi.territory.catalog.application.RegisterItemCommand;
import com.kobi.territory.catalog.domain.item.GrantRule;
import com.kobi.territory.catalog.domain.item.ItemSlot;
import com.kobi.territory.common.model.Rarity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영 아이템 추가(운영 폼). {@code X-Admin-Token} 검사는 조립 모듈(app-api)의 /admin/** 인터셉터가 한다(리더 결정 4 —
 * 4단계 로그인 후 역할 기반으로 대체). 형식·참조·중복 검증은 도메인이 하고 400 INVALID_ITEM_DEFINITION·
 * UNKNOWN_ITEM_REFERENCE, 409 ITEM_ALREADY_EXISTS 로 답한다.
 */
@RestController
@RequestMapping("/admin/items")
public class AdminItemController {

    private final ItemCatalogService items;

    public AdminItemController(ItemCatalogService items) {
        this.items = items;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemView register(@Valid @RequestBody RegisterItemRequest request) {
        LookRequest look = request.look();
        return items.register(new RegisterItemCommand(request.itemId(), request.name(), request.emoji(), request.slot(),
            request.tier(), request.theme(), look == null ? null : look.type(), look == null ? null : look.primary(),
            look == null ? null : look.secondary(), request.grantRule(), request.grantRef(), request.validFrom(),
            request.validTo()));
    }

    /**
     * @param grantRule REGION_VISIT(grantRef = 지역 코드) | PERIOD_CHECK_IN(validFrom·validTo 필수) |
     *                  PROVINCE_CHECK_IN(grantRef = 시·도 코드) | THEME_COMPLETE(grantRef = 세트 id) | MANUAL
     */
    public record RegisterItemRequest(
        @NotBlank @Size(max = 60) String itemId,
        @NotBlank @Size(max = 40) String name,
        @NotBlank @Size(max = 16) String emoji,
        @NotNull ItemSlot slot,
        @NotNull Rarity tier,
        @Size(max = 20) String theme,
        @Valid LookRequest look,
        @NotNull GrantRule.Type grantRule,
        @Size(max = 40) String grantRef,
        LocalDate validFrom,
        LocalDate validTo
    ) {}

    public record LookRequest(@NotBlank String type, @NotBlank String primary, @NotBlank String secondary) {}
}
