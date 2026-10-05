import { useRef, useState, useSyncExternalStore, type FormEvent } from 'react';
import { adminErrorText, DAY_OPTIONS, DEFAULT_DAYS } from '../model/metrics';
import { ADMIN_VIEW_HASH, adminViewOf, lineupErrorText, type AdminView } from '../model/seasonLineups';
import { useMetrics, useSeasonLineups } from '../queries';
import { MetricsDashboard } from './MetricsDashboard';
import { SeasonLineupsPanel } from './SeasonLineupsPanel';

const VIEW_TEXT: Readonly<Record<AdminView, { title: string; lead: string; open: string }>> = {
  metrics: { title: '운영 지표', lead: '익명 방문·탐험가 활동 — 서버가 센 숫자를 그대로 보여 줘요', open: '지표 보기' },
  seasons: { title: '계절 회차', lead: '계절 명소 지역 목록 — 한국관광공사 TourAPI 근거 후보를 보고 확정해요', open: '회차 보기' },
};

function subscribeHash(onChange: () => void) {
  window.addEventListener('hashchange', onChange);
  return () => window.removeEventListener('hashchange', onChange);
}

/**
 * 관리자 화면: 관리자 토큰 입력 → 운영 지표(#/admin) 또는 계절 회차(#/admin/seasons). 토큰은 이 화면의 메모리에만 두고(저장하지 않는다),
 * 새로고침하거나 "잠그기"를 누르면 잊는다. 두 보기를 오가도 같은 토큰을 쓴다.
 */
export function AdminPage() {
  const [adminToken, setAdminToken] = useState<string | null>(null);
  const [session, setSession] = useState(0);
  const [days, setDays] = useState<number>(DEFAULT_DAYS);
  const inputRef = useRef<HTMLInputElement>(null);
  const view = adminViewOf(useSyncExternalStore(subscribeHash, () => location.hash));
  const metrics = useMetrics(adminToken, session, days, view === 'metrics');
  const lineups = useSeasonLineups(adminToken, session, view === 'seasons');
  const current = view === 'metrics' ? metrics : lineups;
  const text = VIEW_TEXT[view];

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setAdminToken(inputRef.current?.value.trim() ?? '');
    setSession(previous => previous + 1);
    if (inputRef.current) inputRef.current.value = '';
  };
  const lock = () => {
    setAdminToken(null);
    setSession(previous => previous + 1);
  };

  return (
    <div className="wrap admin" id="admin" data-view={view}>
      <header className="admin-head">
        <div className="brand">
          <h1>나의 영토 <small>{text.title}</small></h1>
          <p>{text.lead}</p>
        </div>
        <nav className="admin-nav" id="admin-nav">
          <a className={`btn sm ${view === 'metrics' ? 'on' : ''}`} id="admin-nav-metrics" href={ADMIN_VIEW_HASH.metrics}>운영 지표</a>
          <a className={`btn sm ${view === 'seasons' ? 'on' : ''}`} id="admin-nav-seasons" href={ADMIN_VIEW_HASH.seasons}>계절 회차</a>
          <a className="btn sm" href="/">게임으로</a>
        </nav>
      </header>
      <form className="card admin-login" id="admin-login" onSubmit={submit} hidden={current.isSuccess && adminToken !== null}>
        <label htmlFor="admin-token">관리자 토큰</label>
        <input id="admin-token" ref={inputRef} type="password" autoComplete="off" spellCheck={false} placeholder="X-Admin-Token" />
        <button className="btn primary" id="admin-open" type="submit">{text.open}</button>
        <p className="note">토큰은 이 탭의 메모리에만 있어요 — 저장하지 않고, 새로고침하면 다시 입력해야 해요.</p>
      </form>
      {current.isError ? (
        <p className="admin-error" id="admin-error" role="alert">{view === 'metrics' ? adminErrorText(current.error) : lineupErrorText(current.error)}</p>
      ) : null}
      {current.isPending && adminToken !== null ? <p className="note" id="admin-loading">불러오는 중…</p> : null}
      {current.data && adminToken !== null ? (
        <div className="admin-bar">
          {view === 'metrics' ? (
            <>
              <label htmlFor="admin-days">기간</label>
              <select id="admin-days" value={days} onChange={event => setDays(Number(event.target.value))}>
                {DAY_OPTIONS.map(option => <option key={option} value={option}>{option}일</option>)}
              </select>
            </>
          ) : null}
          <button className="btn sm" id="admin-refresh" type="button" onClick={() => void current.refetch()} disabled={current.isFetching}>새로고침</button>
          <button className="btn sm" id="admin-lock" type="button" onClick={lock}>잠그기</button>
        </div>
      ) : null}
      {view === 'metrics' && metrics.data && adminToken !== null ? <MetricsDashboard metrics={metrics.data} adminToken={adminToken} /> : null}
      {view === 'seasons' && lineups.data && adminToken !== null ? (
        <SeasonLineupsPanel key={session} lineups={lineups.data} adminToken={adminToken} session={session} />
      ) : null}
    </div>
  );
}
