import type { KFactorView, TopErrorsView } from '../../../api/types/analytics';
import { countText, kFactorText } from '../model/metrics';

/** 상위 오류 코드(7일, 화면에 보인 오류 토스트) + K 계수 내역(30일) */
export function TopErrorsCard({ errors, kFactor }: { errors: TopErrorsView; kFactor: KFactorView }) {
  return (
    <div className="card" id="admin-errors">
      <h2>상위 오류 코드 <span>{errors.from} ~ {errors.to} · 모두 {countText(errors.total)}번</span></h2>
      {errors.codes.length ? (
        <table className="admin-table">
          <thead><tr><th>코드</th><th>횟수</th></tr></thead>
          <tbody>
            {errors.codes.map(error => <tr key={error.code} data-error-code={error.code}><th scope="row"><code>{error.code}</code></th><td>{countText(error.count)}</td></tr>)}
          </tbody>
        </table>
      ) : <p className="empty">이 기간에 보인 오류 토스트가 없어요.</p>}
      <h2 style={{ marginTop: 16 }}>K 계수 <span>{kFactor.from} ~ {kFactor.to}</span></h2>
      <dl className="admin-k" id="admin-k">
        <div><dt>초대 유입 가입</dt><dd>{countText(kFactor.invitedNewExplorers)}</dd></div>
        <div><dt>카드·프로필 유입 가입</dt><dd>{countText(kFactor.cardNewExplorers)}</dd></div>
        <div><dt>바이럴 가입(한 사람 한 번)</dt><dd>{countText(kFactor.viralNewExplorers)}</dd></div>
        <div><dt>활동 탐험가</dt><dd>{countText(kFactor.activeExplorers)}</dd></div>
        <div><dt>K</dt><dd data-value={kFactorText(kFactor.value)}>{kFactorText(kFactor.value)}</dd></div>
      </dl>
    </div>
  );
}
