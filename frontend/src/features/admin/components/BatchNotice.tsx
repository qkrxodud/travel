import { missingDaysText } from '../model/metrics';
import { useRunBatch } from '../queries';

/** 배치가 아직 계산하지 않은 날이 있으면 안내 + 지금 실행 */
export function BatchNotice({ missingDays, adminToken }: { missingDays: readonly string[]; adminToken: string }) {
  const runBatch = useRunBatch(adminToken);
  const text = missingDaysText(missingDays);
  if (!text) return null;
  return (
    <div className="admin-notice" id="admin-missing">
      <span>{text}</span>
      <button className="btn sm" id="admin-batch" type="button" disabled={runBatch.isPending} onClick={() => runBatch.mutate()}>
        {runBatch.isPending ? '실행 중…' : '배치 실행'}
      </button>
      {runBatch.isError ? <span className="admin-error">배치를 실행하지 못했어요</span> : null}
    </div>
  );
}
