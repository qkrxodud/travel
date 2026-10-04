import { useRef } from 'react';
import { toClientCode } from '../../../api/client';
import { TIER_LABEL } from '../../../shared/lib/region/catalog';
import { toast, toastError } from '../../../store/toastStore';
import { useUiStore } from '../../../store/uiStore';
import { useCatalog } from '../../../shared/queries/catalog';
import { useCollection } from '../../../shared/queries/collection';
import { meIn, memberName } from '../../../shared/lib/territory/visits';
import { setChips, showsProvinceFirstHint } from '../model/territory';
import { useDispute, useEditVisit } from '../queries';
import { useMyTerritory } from '../../../shared/queries/territory';
import { useMapActions } from '../queries';
import { RevisitStamp } from './RevisitStamp';
import { WishToggle } from './WishToggle';
import { track } from '../../../api/analytics';

const EMPTY_HINT = '지도에서 지역을 선택하면 방문 날짜와 한 줄 메모를 남길 수 있어요.';

/** 선택한 지역(#detail): 기록 남기기·수정·제거·여행 카드, 공유 지도면 멤버별 방문과 지도장 이의 */
export function RegionDetail() {
  const selected = useUiStore(state => state.selected);
  const openCheckin = useUiStore(state => state.openCheckin);
  const catalog = useCatalog();
  const { mapId, territory, detail, visits } = useMyTerritory();
  const { data: collection } = useCollection();
  const { cancel, showRecentCard } = useMapActions(catalog, visits, mapId);
  const editVisit = useEditVisit();
  const dispute = useDispute();
  const dateRef = useRef<HTMLInputElement>(null);
  const memoRef = useRef<HTMLInputElement>(null);

  const feature = selected && catalog ? catalog.byCode.get(selected) : undefined;
  if (!selected || !feature || !catalog) {
    return (
      <div className="card detail" id="detail">
        <h2>선택한 지역 <span>기록 남기기</span></h2>
        <div id="d-body"><p className="empty">{EMPTY_HINT}</p></div>
      </div>
    );
  }
  const tier = feature.properties.tier;
  const visit = visits?.get(selected);
  const mine = new Set(visits?.keys() ?? []);
  const provinceHint = showsProvinceFirstHint(catalog, selected, mine);
  const chips = setChips(collection?.sets ?? [], selected, mine);
  const xp = catalog.xpByTier[tier];
  const me = meIn(detail);
  const isOwner = !!me && me.role === 'OWNER';
  const sharedVisits = detail && territory ? territory.visits.filter(row => toClientCode(row.regionCode) === selected) : [];

  const save = async () => {
    try {
      await editVisit.mutateAsync({ code: selected, visitDate: dateRef.current?.value ?? '', memo: (memoRef.current?.value ?? '').trim(), mapId });
      toast('✓', '기록 저장됨', feature.properties.name);
    } catch (error) {
      toastError(error);
    }
  };
  const flag = async (memberId: string, disputed: boolean) => {
    if (!detail) return;
    try {
      await dispute.mutateAsync({ mapId: detail.mapId, code: selected, memberId, disputed });
      toast('✓', disputed ? '이의 표시' : '이의 해제', '지도 내 랭킹 집계에서만 빠져요');
    } catch (error) {
      toastError(error);
    }
  };

  return (
    <div className="card detail" id="detail">
      <h2>선택한 지역 <span>기록 남기기</span></h2>
      <div id="d-body">
        <div className="name">{feature.properties.name}<span className={`rar ${tier}`}>{TIER_LABEL[tier]} · XP {xp}</span></div>
        <div className="sub">{`${feature.properties.prov} · ${visit ? '내 영토' : '아직 미탐험'}${provinceHint ? ` · 첫 방문 시 +${catalog.provinceFirstBonus}` : ''}`}</div>
        {chips.length ? (
          <div className="setchips">{chips.map(chip => <span className="setchip" key={chip.id}>{chip.name} <b>{chip.have}/{chip.total}</b></span>)}</div>
        ) : null}
        {visit ? (
          <>
            <div className="row" key={`${selected}:${visit.date}:${visit.memo}`}>
              <input type="date" id="d-date" ref={dateRef} defaultValue={visit.date || ''} aria-label="방문 날짜" />
              <input type="text" id="d-memo" ref={memoRef} maxLength={40} placeholder="한 줄 메모" defaultValue={visit.memo || ''} aria-label="메모" />
            </div>
            <div className="row">
              <button className="btn primary" id="d-save" onClick={() => void save()}>기록 저장</button>
              <button className="btn" id="d-card" onClick={() => {
                track('share_click', { target: 'travel' });
                showRecentCard(selected);
              }}>여행 카드</button>
              <button className="btn danger" id="d-remove" onClick={() => void cancel(selected)}>영토에서 제거</button>
            </div>
          </>
        ) : (
          <div className="row"><button className="btn primary" id="d-visit" onClick={() => openCheckin(selected)}>방문 체크 (+{xp} XP)</button></div>
        )}
        <RevisitStamp code={selected} name={feature.properties.name} />
        <WishToggle code={selected} name={feature.properties.name} />
        {sharedVisits.length ? (
          <ul className="vlist" id="d-members">
            {sharedVisits.map(row => {
              const member = detail?.members.find(candidate => candidate.explorerId === row.checkedInBy);
              const isMe = !!member?.me;
              return (
                <li key={row.checkedInBy} data-visitor={row.checkedInBy}>
                  <span className="dot" style={{ background: member?.color ?? '#999' }} />
                  {memberName({ me: isMe, explorerId: row.checkedInBy })} · {row.visitDate}
                  {row.claim ? <>{' '}<span className="tag-claim">선점</span></> : null}
                  {row.disputed ? <>{' '}<span className="tag-disputed">이의</span></> : null}
                  {isOwner && !isMe ? (
                    <>{' '}<button className="btn sm" data-dispute={row.checkedInBy} data-flag={String(!row.disputed)} onClick={() => void flag(row.checkedInBy, !row.disputed)}>{row.disputed ? '이의 해제' : '이의 표시'}</button></>
                  ) : null}
                </li>
              );
            })}
          </ul>
        ) : null}
      </div>
    </div>
  );
}
