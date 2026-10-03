import { useProgress } from '../../../shared/queries/progress';
import { freezeBadge, freezeProgressText } from '../../../shared/lib/progress/freeze';
import { useUiStore } from '../../../store/uiStore';
import { milestoneRows, nextMilestoneText, streakStrip, streakText } from '../model/streak';
import { useQuests } from '../queries';
import { QuestRow } from './QuestRow';

const LOADING = <p className="empty">불러오는 중…</p>;

/** 퀘스트 탭: 이번 달 퀘스트(받기 1회) · 탐험 스트릭(보호권·연속 탐험 마일스톤) · 상시 도전 — 서버 값 */
export function QuestsTab() {
  const tab = useUiStore(state => state.tab);
  const { data: quests } = useQuests();
  const { data: progress } = useProgress();
  const ready = quests && progress;
  const freeze = ready ? freezeBadge(progress.streakFreeze) : null;
  return (
    <section id="tab-quests" hidden={tab !== 'quests'} className="grid2">
      <div className="card">
        <h2>이번 달 퀘스트 <span id="q-month">{ready ? `${Number(quests.month.slice(5))}월 · ${quests.monthlyDone}/${quests.monthly.length} 완료` : ''}</span></h2>
        <div id="quests-month">{ready ? quests.monthly.map(quest => <QuestRow key={quest.id} quest={quest} />) : LOADING}</div>
      </div>
      <div className="side">
        <div className="card">
          <h2>탐험 스트릭 <span>매달 새 지역 1곳이면 유지</span></h2>
          <div className="sub" id="streak-txt">{ready ? streakText(progress.streak, progress.streakFreeze) : ''}</div>
          <div className="streak" id="streak">
            {ready ? streakStrip(progress.streak, quests.month).map(cell => (
              <span key={cell.month} data-month={cell.month} className={`${cell.on ? 'on' : ''} ${cell.frozen ? 'frz' : ''} ${cell.now ? 'now' : ''}`} title={cell.frozen ? '보호권으로 지킨 달' : undefined}>
                {cell.frozen ? '🧊' : cell.label}
              </span>
            )) : null}
          </div>
          {ready && freeze ? (
            <div className="freeze-line" id="freeze-progress" title={freeze.hint}>
              <span className={`freeze ${freeze.empty ? 'dim' : ''}`} aria-label={`스트릭 보호권 ${progress.streakFreeze.held}개`}><span className="ico" aria-hidden="true">{freeze.icon}</span>{freeze.count}</span>
              <span>{freezeProgressText(progress.streakFreeze)}</span>
            </div>
          ) : null}
          {ready ? (
            <div className="milestones" id="milestones">
              <div className="sub" id="milestone-next">{nextMilestoneText(progress.nextMilestone)}</div>
              {milestoneRows(progress.milestones).map(row => (
                <div key={row.months} className={`milestone ${row.reached ? 'done' : ''}`} data-milestone={row.months} data-reached={String(row.reached)}>
                  <span className="m">{row.label}</span>
                  <span className="bar"><i style={{ width: `${row.percent}%` }} /></span>
                  <span className="st">{row.status}</span>
                  <span className="rw">{row.reward}</span>
                </div>
              ))}
            </div>
          ) : null}
        </div>
        <div className="card">
          <h2>상시 도전 <span>완료하면 칭호</span></h2>
          <div id="quests-long">{ready ? quests.always.map(quest => <QuestRow key={quest.id} quest={quest} />) : LOADING}</div>
        </div>
      </div>
    </section>
  );
}
