import { useEffect, useRef } from 'react';
import { localIsoDate, setChips } from '../model/territory';
import { pixelRenderer } from '../../../shared/lib/pixel';
import { catalogItem, itemOrigin, SLOT_NAME } from '../../../shared/lib/item/displayItem';
import { TIER_LABEL } from '../../../shared/lib/region/catalog';
import { Modal } from '../../../shared/ui/Modal';
import { toast, toastError } from '../../../store/toastStore';
import { useUiStore } from '../../../store/uiStore';
import { useCatalog } from '../../../shared/queries/catalog';
import { useCollection } from '../../../shared/queries/collection';
import { useCheckIn, usePreview } from '../queries';
import { useMyTerritory } from '../../../shared/queries/territory';
import { useMapActions } from '../queries';

/**
 * 체크인 모달(#checkin): 지역을 누르면 미리보기(GET /visits/preview — 예상 XP·받을 아이템)가 온 뒤에 열린다.
 * 저장하면 영토를 다시 읽고 닫힌다. 진행·가방은 반영 대기 창에서 따라온다.
 */
export function CheckinModal() {
  const code = useUiStore(state => state.checkinCode);
  const closeCheckin = useUiStore(state => state.closeCheckin);
  const catalog = useCatalog();
  const { mapId, detail, visits } = useMyTerritory();
  const { data: collection } = useCollection();
  const preview = usePreview(code, mapId);
  const checkIn = useCheckIn();
  const { showRecentCard } = useMapActions(catalog, visits, mapId);
  const dateRef = useRef<HTMLInputElement>(null);
  const memoRef = useRef<HTMLInputElement>(null);
  const photoRef = useRef<HTMLInputElement>(null);

  // 미리보기 실패: 토스트(쿼리 캐시)만 띄우고 열지 않는다
  useEffect(() => {
    if (preview.isError) closeCheckin();
  }, [preview.isError, closeCheckin]);

  const feature = code && catalog ? catalog.byCode.get(code) : undefined;
  const open = !!(code && feature && preview.data);
  const view = code && catalog ? catalog.itemByRegion.get(code) : undefined;
  const item = view && catalog ? catalogItem(view, itemOrigin(view.itemId, catalog, () => undefined)) : null;
  const itemName = preview.data?.items[0]?.name ?? item?.name ?? '';

  const save = async (): Promise<boolean> => {
    if (!code || !feature) return false;
    try {
      const result = await checkIn.mutateAsync({
        code,
        visitDate: dateRef.current?.value ?? '',
        memo: (memoRef.current?.value ?? '').trim(),
        photoUrl: (photoRef.current?.value ?? '').trim(),
        mapId,
      });
      useUiStore.getState().sendMapCommand({ kind: 'ping', code });
      closeCheckin();
      toast('✓', `${feature.properties.name} · ${result.nth}번째 영토`, `예상 XP +${result.xp.total} · ${itemName}`);
      return true;
    } catch (error) {
      toastError(error);
      return false;
    }
  };

  const saveAndCard = async () => {
    if (!code) return;
    const date = dateRef.current?.value ?? '';
    const memo = (memoRef.current?.value ?? '').trim();
    if (await save()) {
      // 저장 직후의 내 영토(방금 칠한 곳 포함 — 가장 최근 처리)로 카드를 그린다
      const after = new Map(visits ?? []);
      after.set(code, { code, date, memo, at: Date.now() });
      showRecentCard(code, after);
    }
  };

  let body = null;
  if (open && feature && preview.data && catalog && code) {
    const tier = feature.properties.tier;
    const prov = feature.properties.prov;
    const mine = new Set(visits?.keys() ?? []);
    const chips = setChips(collection?.sets ?? [], code, mine, code);
    const xp = preview.data.xp;
    body = (
      <>
        <div className="stamp">
          <div className={`ring ${tier}`}>{feature.properties.name}</div>
          <h3>{preview.data.nth}번째 영토</h3>
          <div className="sub">{`${prov} · ${TIER_LABEL[tier]} 지역${tier === 'legend' ? ' · 전설 지역 발견!' : ''}${preview.data.firstInProvince ? ` · ${prov} 첫 방문` : ''}`}</div>
        </div>
        <div className="gains" id="ci-gains">
          {xp.lines.map(line => (
            <div key={line.source} className={line.source === 'REGION_BASE' ? '' : 'bonus'}><span>{line.label}</span><b>+{line.amount}</b></div>
          ))}
          <div className="total"><span>예상 획득 XP</span><b id="ci-xp-total">+{xp.total}</b></div>
        </div>
        {chips.length ? (
          <div className="setchips">{chips.map(chip => <span className="setchip" key={chip.id}>{chip.name} <b>{chip.have}/{chip.total}</b>{chip.done ? ' 완성!' : ''}</span>)}</div>
        ) : null}
        {item ? (
          <div className={`loot ${item.tier}`}>
            <div className="e"><img className="px big" src={pixelRenderer.itemUrl(item)} alt="" /></div>
            <div>
              <div className="t">받을 특산물 · <span className={`rar ${item.tier}`}>{TIER_LABEL[item.tier]}</span></div>
              <div className="n" id="ci-item">{itemName}</div>
              <div className="s">{SLOT_NAME[item.slot]} 슬롯{item.tier === 'legend' ? ' · 전설 풍경이 열려요' : ''}</div>
            </div>
          </div>
        ) : null}
        <div className="row">
          <input type="date" id="ci-date" ref={dateRef} defaultValue={localIsoDate(new Date())} aria-label="방문 날짜" />
          <input type="text" id="ci-memo" ref={memoRef} maxLength={40} placeholder="한 줄 메모 (예: 물회 먹음)" aria-label="메모" />
          {detail?.settings.photoRequired ? <input type="url" id="ci-photo" ref={photoRef} placeholder="사진 주소(이 지도는 사진 필수)" aria-label="사진 주소" /> : null}
        </div>
        <div className="row">
          <button className="btn" id="ci-cancel" onClick={closeCheckin}>취소</button>
          <button className="btn" id="ci-card" disabled={checkIn.isPending} onClick={() => void saveAndCard()}>기록하고 카드</button>
          <button className="btn primary" id="ci-save" disabled={checkIn.isPending} onClick={() => void save()}>기록하고 닫기</button>
        </div>
      </>
    );
  }

  return (
    <Modal id="checkin" boxId="checkin-body" open={open} onClose={closeCheckin}>
      {body}
    </Modal>
  );
}
