import type { LineupScheduleResponse, TourApiStatusResponse } from '../../../api/types/catalog';
import { keyStateText, scheduleText, usageText } from '../model/seasonLineups';

/** TourAPI 연결 상태·오늘 호출 수/하루 예산·경고 — 키 값은 서버가 보내지 않는다(연결 여부만) */
export function TourApiStatusCard({ tourApi, schedule }: { tourApi: TourApiStatusResponse; schedule: LineupScheduleResponse }) {
  return (
    <div className="card lineup-status" id="tourapi-status" data-configured={String(tourApi.configured)} data-exhausted={String(tourApi.exhausted)}>
      <h2>{tourApi.source} <span id="tourapi-key-state">{keyStateText(tourApi)}</span></h2>
      <p className="lineup-usage" id="tourapi-usage" data-calls={tourApi.callsToday} data-limit={tourApi.dailyLimit}>{usageText(tourApi)}</p>
      <p className="note" id="lineup-schedule">{scheduleText(schedule)}</p>
      {tourApi.warnings.length ? (
        <ul className="lineup-warnings" id="tourapi-warnings">
          {tourApi.warnings.map(warning => <li key={warning}>{warning}</li>)}
        </ul>
      ) : null}
    </div>
  );
}
