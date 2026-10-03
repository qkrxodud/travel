import { toast, toastError } from '../../../store/toastStore';
import { useProgress } from '../../../shared/queries/progress';
import { useSelectTitle } from '../queries';

/** 칭호 — 얻은 것만 고를 수 있다(서버) */
export function TitlesCard() {
  const { data: progress } = useProgress();
  const selectTitle = useSelectTitle();
  const choose = async (titleId: string) => {
    try {
      const updated = await selectTitle.mutateAsync(titleId);
      toast('✓', '칭호 변경', updated.title?.name ?? '');
    } catch (error) {
      toastError(error);
    }
  };
  return (
    <div className="card">
      <h2>칭호 <span>프로필에 보일 칭호를 고르세요</span></h2>
      <div className="titles" id="titles">
        {(progress?.titles ?? []).map(title => (
          <button key={title.id} data-title={title.id} aria-pressed={title.selected} disabled={!title.earned} title={title.how} onClick={() => void choose(title.id)}>
            {title.name}{title.earned ? '' : ` · ${title.how}`}
          </button>
        ))}
      </div>
    </div>
  );
}
