import { toastError } from '../../../store/toastStore';
import { useUiStore } from '../../../store/uiStore';
import { useClearVisits, useSeed } from '../queries';

/** 예시 다시 채우기·전부 지우기(local 전용 개발 엔드포인트) */
export function SampleNote() {
  const sampleMode = useUiStore(state => state.sampleMode);
  const seed = useSeed();
  const clear = useClearVisits();
  const fill = async () => {
    try {
      await seed.mutateAsync();
      const store = useUiStore.getState();
      store.select(null);
      store.sendMapCommand({ kind: 'reset' });
    } catch (error) {
      toastError(error);
    }
  };
  const wipe = async () => {
    try {
      await clear.mutateAsync();
      useUiStore.getState().select(null);
    } catch (error) {
      toastError(error);
    }
  };
  return (
    <div className="note">
      <span id="note-txt">{sampleMode ? '예시 데이터가 칠해져 있습니다(서버 시드). "전부 지우기"로 비울 수 있어요.' : '기록은 서버에 저장됩니다.'}</span>
      <button className="btn sm" id="t-sample" onClick={() => void fill()}>예시 다시 채우기</button>
      <button className="btn sm danger" id="t-clear" onClick={() => void wipe()}>전부 지우기</button>
    </div>
  );
}
