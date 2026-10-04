import { useRef } from 'react';
import type { CardKind, MyCardsResponse, ProfileVisibility } from '../../../api/types/sharing';
import { sharingApi } from '../../../api/sharing';
import { track } from '../../../api/analytics';
import { toast, toastError } from '../../../store/toastStore';
import { useUiStore } from '../../../store/uiStore';
import { useMaps } from '../../../shared/queries/territory';
import { normalizeHandle } from '../../../shared/lib/handle';
import { privacyNote, VISIBILITY_OPTIONS } from '../model/sharing';
import { useMyCards, useOpenServerCard, useSetPrivacy } from '../queries';
import { CardThumb } from './CardThumb';
import { ShareMapToggle } from './ShareMapToggle';

const DEFAULT_NOTE = '공개 프로필은 기본 비공개예요. "공개하기"를 켜야 링크가 열려요.';
const KINDS: readonly (readonly [CardKind, string, string])[] = [['territory', '영토 카드', '영토'], ['recent', '최근 여행 카드', '최근 여행'], ['recap', '연간 리캡 카드', '연간 리캡']];

/** 자랑하기(4단계): 서버가 그린 카드 미리보기 · 프로필 링크 · 공개 범위 · VS 카드 · 프로필로 합류할 지도 */
export function ShareCard({ active }: { active: boolean }) {
  const { data: cards } = useMyCards(active);
  const { data: maps } = useMaps();
  const setPrivacy = useSetPrivacy();
  const openServerCard = useOpenServerCard();
  const showCard = useUiStore(state => state.showCard);
  const urlRef = useRef<HTMLDivElement>(null);
  const vsRef = useRef<HTMLInputElement>(null);

  const link = cards?.profileUrl ? location.origin + cards.profileUrl : null;
  const openCard = (kind: CardKind) => {
    track('share_click', { target: kind });
    openServerCard.mutateAsync(kind).then(showCard).catch(error => toastError(error));
  };
  const changePrivacy = async (visibility: ProfileVisibility, label: string) => {
    try {
      await setPrivacy.mutateAsync(visibility);
      toast('✓', '공개 범위', label);
    } catch (error) {
      toastError(error);
    }
  };
  const copy = () => {
    track('link_copy', { target: 'profile' });
    const text = urlRef.current?.textContent ?? '';
    const fallback = () => {
      const range = document.createRange();
      if (urlRef.current) range.selectNodeContents(urlRef.current);
      const selection = getSelection();
      selection?.removeAllRanges();
      selection?.addRange(range);
      toast('!', '복사가 막혀 있어요', '선택된 링크를 직접 복사하세요');
    };
    if (navigator.clipboard?.writeText) navigator.clipboard.writeText(text).then(() => toast('✓', '링크 복사됨', text)).catch(fallback);
    else fallback();
  };
  const vsServerCard = () => {
    const other = normalizeHandle(vsRef.current?.value);
    if (!other || !cards?.handle) return;
    track('share_click', { target: 'vs' });
    showCard(sharingApi.vsCardPath(cards.handle, other));
  };
  const owned = cards?.handle ? (maps ?? []).filter(map => map.kind === 'SHARED' && map.role === 'OWNER') : [];

  return (
    <div className="card" id="share-card">
      <h2>자랑하기 <span>서버가 그린 카드 — 공개 프로필 링크의 미리보기(OG 이미지)로도 쓰임</span></h2>
      <div className="share-thumbs" id="share-thumbs">
        {KINDS.map(([kind, alt, caption]) => <CardThumb key={kind} kind={kind} alt={alt} caption={caption} enabled={active && !!cards} />)}
      </div>
      <div className="cards" style={{ marginTop: 8 }}>
        <button className="btn primary" id="t-share" onClick={() => openCard('territory')}>영토 카드</button>
        <button className="btn" id="t-recent-card" onClick={() => openCard('recent')}>최근 여행 카드</button>
        <button className="btn" id="t-recap-card" onClick={() => openCard('recap')}>연간 리캡 카드</button>
      </div>
      <div className="url" style={{ marginTop: 10 }} id="share-url" ref={urlRef}>{link || '로그인하면 공개 프로필 링크가 생겨요'}</div>
      <div className="row">
        <button className="btn sm" id="t-copy" disabled={!link} onClick={copy}>프로필 링크 복사</button>
        <a className="btn sm" id="t-open-profile" target="_blank" rel="noopener" hidden={!link} href={cards?.profileUrl ?? undefined}>공개 프로필 열기</a>
      </div>
      <PrivacyRow cards={cards ?? null} onChange={(visibility, label) => void changePrivacy(visibility, label)} />
      <div className="share-vs" id="share-vs" hidden={!(cards?.handle && cards.publiclyVisible)}>
        <input id="vs-handle" ref={vsRef} placeholder="친구 handle" maxLength={20} aria-label="비교할 친구 handle" />
        <button className="btn sm" id="t-vs-server" onClick={vsServerCard}>VS 카드</button>
        <span className="note" id="share-vs-basis">개인 지도 기준(랭킹 탭 비교는 모든 지도 기준)</span>
      </div>
      <div className="share-maps" id="share-maps" hidden={!owned.length}>
        {owned.length ? (
          <>
            <div className="note">프로필 링크로 합류할 수 있는 지도(초대코드 없이)</div>
            {owned.map(map => <ShareMapToggle key={map.mapId} mapId={map.mapId} name={map.name} memberCount={map.memberCount} />)}
          </>
        ) : null}
      </div>
    </div>
  );
}

function PrivacyRow({ cards, onChange }: { cards: MyCardsResponse | null; onChange: (visibility: ProfileVisibility, label: string) => void }) {
  return (
    <div className="share-privacy" id="share-privacy" hidden={!!cards && !cards.handle}>
      <button className="btn sm primary" id="t-publish" hidden={!!cards?.publiclyVisible} onClick={() => onChange('PUBLIC', '전체 공개 — 프로필 링크가 열렸어요')}>공개하기</button>
      <label htmlFor="privacy-select">공개 범위</label>
      <select id="privacy-select" value={cards?.visibility ?? 'PRIVATE'}
        onChange={event => onChange(event.target.value as ProfileVisibility, event.target.selectedOptions[0]?.textContent ?? '')}>
        {VISIBILITY_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
      <p className="note" id="privacy-note">{cards ? privacyNote(cards.visibility) : DEFAULT_NOTE}</p>
    </div>
  );
}
