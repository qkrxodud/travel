import { useRef, useState, type FormEvent } from 'react';
import { adminErrorText, DAY_OPTIONS, DEFAULT_DAYS } from '../model/metrics';
import { useMetrics } from '../queries';
import { MetricsDashboard } from './MetricsDashboard';

/**
 * 운영 지표 화면: 관리자 토큰 입력 → GET /admin/metrics. 토큰은 이 화면의 메모리에만 두고(저장하지 않는다),
 * 새로고침하거나 "잠그기"를 누르면 잊는다.
 */
export function AdminPage() {
  const [adminToken, setAdminToken] = useState<string | null>(null);
  const [session, setSession] = useState(0);
  const [days, setDays] = useState<number>(DEFAULT_DAYS);
  const inputRef = useRef<HTMLInputElement>(null);
  const metrics = useMetrics(adminToken, session, days);

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
    <div className="wrap admin" id="admin">
      <header className="admin-head">
        <div className="brand">
          <h1>나의 영토 <small>운영 지표</small></h1>
          <p>익명 방문·탐험가 활동 — 서버가 센 숫자를 그대로 보여 줘요</p>
        </div>
        <a className="btn sm" href="/">게임으로</a>
      </header>
      <form className="card admin-login" id="admin-login" onSubmit={submit} hidden={metrics.isSuccess && adminToken !== null}>
        <label htmlFor="admin-token">관리자 토큰</label>
        <input id="admin-token" ref={inputRef} type="password" autoComplete="off" spellCheck={false} placeholder="X-Admin-Token" />
        <button className="btn primary" id="admin-open" type="submit">지표 보기</button>
        <p className="note">토큰은 이 탭의 메모리에만 있어요 — 저장하지 않고, 새로고침하면 다시 입력해야 해요.</p>
      </form>
      {metrics.isError ? <p className="admin-error" id="admin-error" role="alert">{adminErrorText(metrics.error)}</p> : null}
      {metrics.isPending && adminToken !== null ? <p className="note" id="admin-loading">불러오는 중…</p> : null}
      {metrics.data && adminToken !== null ? (
        <>
          <div className="admin-bar">
            <label htmlFor="admin-days">기간</label>
            <select id="admin-days" value={days} onChange={event => setDays(Number(event.target.value))}>
              {DAY_OPTIONS.map(option => <option key={option} value={option}>{option}일</option>)}
            </select>
            <button className="btn sm" id="admin-refresh" type="button" onClick={() => void metrics.refetch()} disabled={metrics.isFetching}>새로고침</button>
            <button className="btn sm" id="admin-lock" type="button" onClick={lock}>잠그기</button>
          </div>
          <MetricsDashboard metrics={metrics.data} adminToken={adminToken} />
        </>
      ) : null}
    </div>
  );
}
