import type { QuestResponse } from '../../../api/types/progression';
import { Bar } from '../../../shared/ui/Bar';
import { toast, toastError } from '../../../store/toastStore';
import { percentOf } from '../model/streak';
import { useClaimQuest } from '../queries';

/** 퀘스트 한 줄: 받음 / 받기 버튼 / 진행 수 */
export function QuestRow({ quest }: { quest: QuestResponse }) {
  const claim = useClaimQuest();
  const receive = async () => {
    try {
      const claimed = await claim.mutateAsync(quest.id);
      toast('★', '퀘스트 보상', `+${claimed.xp} XP`);
    } catch (error) {
      toastError(error);
    }
  };
  const status = quest.claimed ? '받음'
    : quest.claimable ? <button className="btn sm primary" data-claim={quest.id} disabled={claim.isPending} onClick={() => void receive()}>받기</button>
      : `${quest.current}/${quest.target}`;
  return (
    <div className={`quest ${quest.achieved ? 'done' : ''}`} data-quest={quest.id}>
      <div className="ico">{quest.ico}</div>
      <div className="body">
        <div className="t">{quest.name}</div>
        <div className="d">{quest.desc}{quest.title ? ` · 칭호 「${quest.title}」` : ''}</div>
        <Bar percent={percentOf(quest.current, quest.target)} gold={quest.achieved} />
      </div>
      <div className="xp">
        <span className="st">{status}</span><br />
        <small style={{ fontFamily: 'var(--body)', fontSize: 11, color: 'var(--muted)' }}>+{quest.xp} XP</small>
      </div>
    </div>
  );
}
