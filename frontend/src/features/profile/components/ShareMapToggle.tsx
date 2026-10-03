import { useRef } from 'react';
import { toast, toastError } from '../../../store/toastStore';
import { useMapDetail } from '../../../shared/queries/territory';
import { useSetMapVisibility } from '../queries';

/** 프로필 링크로 합류할 수 있게 공개할 공유 지도(지도장만). 누르면 바로 바뀌고, 실패하면 되돌린다. */
export function ShareMapToggle({ mapId, name, memberCount }: { mapId: string; name: string; memberCount: number }) {
  const { data: detail } = useMapDetail(mapId);
  const setVisibility = useSetMapVisibility();
  const boxRef = useRef<HTMLInputElement>(null);
  const serverChecked = detail?.settings.visibility === 'PUBLIC';
  const toggle = async (checked: boolean) => {
    try {
      const saved = await setVisibility.mutateAsync({ mapId, visible: checked });
      toast('✓', checked ? '프로필에 지도 공개' : '프로필에서 지도 숨김', saved.name);
    } catch (error) {
      toastError(error);
      if (boxRef.current) boxRef.current.checked = !checked;
    }
  };
  return (
    <label>
      {/* 서버 값이 바뀌면 다시 맞춘다(key) — 그 사이 누른 값은 화면이 바로 보여 준다 */}
      <input type="checkbox" ref={boxRef} key={String(serverChecked)} data-profile-map={mapId} defaultChecked={serverChecked} onChange={event => void toggle(event.target.checked)} /> {name} · {memberCount}명
    </label>
  );
}
