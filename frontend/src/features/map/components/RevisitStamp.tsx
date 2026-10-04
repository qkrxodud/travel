import type { ApiError } from '../../../api/client';
import { toast, toastError } from '../../../store/toastStore';
import { revisitView, stampedToast, stampErrorText } from '../model/revisit';
import { useRevisitStatus, useStamp } from '../queries';

/** 지역 상세의 재방문 도장(#d-revisit-box) — 탐험가 단위로 칠한 곳에만 보인다. 받을 수 있는지·이유·도장 연도는 서버 판정 */
export function RevisitStamp({ code, name }: { code: string; name: string }) {
  const { data: status } = useRevisitStatus(code);
  const stamp = useStamp();
  const view = revisitView(status);
  if (!view) return null;
  const press = async () => {
    try {
      const stamped = await stamp.mutateAsync(code);
      const message = stampedToast(stamped);
      toast('🎫', message.title, message.sub);
    } catch (error) {
      toastError(error, stampErrorText((error as Partial<ApiError>).code, name) ?? undefined);
    }
  };
  return (
    <div className="revisit" id="d-revisit-box" data-can-stamp={String(view.canStamp)}>
      <div className="row">
        <button className="btn" id="d-revisit" disabled={!view.canStamp || stamp.isPending} onClick={() => void press()}>{view.label}</button>
      </div>
      {view.why ? <p className="why" id="d-revisit-why">{view.why}</p> : null}
      <div className="stamps" id="d-stamps">
        <span className="since">{view.since}</span>
        {view.years.length
          ? view.years.map(year => <span className="stamp" key={year} data-year={year}>🎫 {year}</span>)
          : <span className="none">아직 재방문 도장이 없어요</span>}
      </div>
    </div>
  );
}
