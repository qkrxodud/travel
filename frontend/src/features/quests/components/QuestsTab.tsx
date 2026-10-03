import { useProgress } from '../../../shared/queries/progress';
import { useUiStore } from '../../../store/uiStore';
import { streakStrip, streakText } from '../model/streak';
import { useQuests } from '../queries';
import { QuestRow } from './QuestRow';

const LOADING = <p className="empty">불러오는 중…</p>;

/** 퀘스트 탭: 이번 달 퀘스트(받기 1회) · 탐험 스트릭 · 상시 도전 — 서버 값 */
export function QuestsTab() {
  const tab = useUiStore(state => state.tab);
  const { data: quests } = useQuests();
  const { data: progress } = useProgress();
  const ready = quests && progress;
  return (
    <section id="tab-quests" hidden={tab !== 'quests'} className="grid2">
      <div className="card">
        <h2>이번 달 퀘스트 <span id="q-month">{ready ? `${Number(quests.month.slice(5))}월 · ${quests.monthlyDone}/${quests.monthly.length} 완료` : ''}</span></h2>
        <div id="quests-month">{ready ? quests.monthly.map(quest => <QuestRow key={quest.id} quest={quest} />) : LOADING}</div>
      </div>
      <div className="side">
        <div className="card">
          <h2>탐험 스트릭 <span>매달 새 지역 1곳이면 유지</span></h2>
          <div className="sub" id="streak-txt">{ready ? streakText(progress.streak) : ''}</div>
          <div className="streak" id="streak">
            {ready ? streakStrip(progress.streak, new Date(), quests.month).map(cell => (
              <span key={cell.month} className={`${cell.on ? 'on' : ''} ${cell.now ? 'now' : ''}`}>{cell.label}</span>
            )) : null}
          </div>
        </div>
        <div className="card">
          <h2>상시 도전 <span>완료하면 칭호</span></h2>
          <div id="quests-long">{ready ? quests.always.map(quest => <QuestRow key={quest.id} quest={quest} />) : LOADING}</div>
        </div>
      </div>
    </section>
  );
}
