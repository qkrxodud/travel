import { useState } from 'react';
import type { MapDetailResponse } from '../../../api/types/exploration';
import { toast, toastError } from '../../../store/toastStore';
import { useUiStore } from '../../../store/uiStore';
import { meIn, memberName } from '../../../shared/lib/territory/visits';
import { useMaps, useMyTerritory } from '../../../shared/queries/territory';
import { useRegenerateInvite, useSaveSettings } from '../queries';

/** 지도 카드(#map-card): 지도 선택·만들기·합류, 공유 지도면 멤버·초대코드·지도장 설정·탈퇴 */
export function SharedMapCard() {
  const { territory, detail } = useMyTerritory();
  const { data: maps } = useMaps();
  const setMapId = useUiStore(state => state.setMapId);
  const openMapModal = useUiStore(state => state.openMapModal);
  const current = territory?.mapId ?? '';

  const choose = (mapId: string) => {
    const personal = maps?.find(map => map.kind === 'PERSONAL');
    setMapId(personal && mapId === personal.mapId ? null : mapId);
  };
  const leave = () => {
    if (!detail) return;
    openMapModal({ kind: 'leave', mapId: detail.mapId, name: detail.name, regionCount: meIn(detail)?.regionCount ?? 0 });
  };

  return (
    <div className="card" id="map-card">
      <h2>지도 <span id="map-kind">{detail ? '공유 지도' : '개인 지도'}</span></h2>
      <div className="row" style={{ marginTop: 0 }}>
        <select id="map-select" aria-label="지도 선택" value={current} onChange={event => choose(event.target.value)}>
          {(maps ?? []).map(map => (
            <option key={map.mapId} value={map.mapId} data-kind={map.kind}>
              {map.kind === 'PERSONAL' ? `${map.name} (개인)` : `${map.name} · ${map.memberCount}명`}
            </option>
          ))}
        </select>
      </div>
      <div className="row">
        <button className="btn sm" id="t-map-new" onClick={() => openMapModal({ kind: 'create' })}>지도 만들기</button>
        <button className="btn sm" id="t-map-join" onClick={() => openMapModal({ kind: 'join' })}>초대코드로 합류</button>
      </div>
      <div id="map-shared" hidden={!detail}>
        {detail ? <SharedMapBody detail={detail} onLeave={leave} /> : null}
      </div>
    </div>
  );
}

function SharedMapBody({ detail, onLeave }: { detail: MapDetailResponse; onLeave: () => void }) {
  const isOwner = meIn(detail)?.role === 'OWNER';
  const regenerate = useRegenerateInvite();
  const regenerateInvite = async () => {
    try {
      const result = await regenerate.mutateAsync(detail.mapId);
      toast('✓', '초대코드 재발급', result.inviteCode);
    } catch (error) {
      toastError(error);
    }
  };
  return (
    <>
      <ul className="members" id="map-members">
        {detail.members.map(member => (
          <li key={member.explorerId} data-member={member.explorerId} data-color={member.color}>
            <span className="dot" style={{ background: member.color }} />
            <b>{memberName(member)}</b>
            <span className="role">{member.role === 'OWNER' ? '지도장' : '멤버'}</span>
            <span className="cnt">영토 {member.regionCount} · 선점 {member.claimCount}</span>
          </li>
        ))}
        {detail.departing ? <li className="sub">탈퇴 유예 중 {detail.departing}명(7일 안에 돌아오면 복구)</li> : null}
      </ul>
      <div className="row">
        <span className="sub">초대코드</span><b id="map-invite">{detail.inviteCode}</b>
        <button className="btn sm" id="t-invite-regen" hidden={!isOwner} onClick={() => void regenerateInvite()}>재발급</button>
      </div>
      {/* 서버 설정이 바뀌면 입력칸을 서버 값으로 다시 맞춘다 */}
      <MapSettingsRow key={`${detail.mapId}:${detail.settings.photoRequired}:${detail.settings.dailyCheckInCap}`} detail={detail} hidden={!isOwner} />
      <div className="row"><button className="btn sm danger" id="t-map-leave" onClick={onLeave}>지도에서 탈퇴</button></div>
    </>
  );
}

function MapSettingsRow({ detail, hidden }: { detail: MapDetailResponse; hidden: boolean }) {
  const [photoRequired, setPhotoRequired] = useState(detail.settings.photoRequired);
  const [cap, setCap] = useState(String(detail.settings.dailyCheckInCap));
  const saveSettings = useSaveSettings();
  const save = async () => {
    try {
      const saved = await saveSettings.mutateAsync({
        mapId: detail.mapId,
        settings: { photoRequired, dailyCheckInCap: Number(cap), visibility: detail.settings.visibility },
      });
      toast('✓', '지도 설정 저장', `사진 필수 ${saved.settings.photoRequired ? '켬' : '끔'} · 하루 ${saved.settings.dailyCheckInCap}곳`);
    } catch (error) {
      toastError(error);
    }
  };
  return (
    <div className="row" id="map-settings" hidden={hidden}>
      <label><input type="checkbox" id="set-photo" checked={photoRequired} onChange={event => setPhotoRequired(event.target.checked)} /> 사진 필수</label>
      <label>하루 상한 <input type="number" id="set-cap" min={1} max={50} style={{ width: 52 }} value={cap} onChange={event => setCap(event.target.value)} /></label>
      <button className="btn sm" id="t-settings-save" onClick={() => void save()}>설정 저장</button>
    </div>
  );
}
