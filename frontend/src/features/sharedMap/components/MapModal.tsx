import { useRef } from 'react';
import { Modal } from '../../../shared/ui/Modal';
import { toast, toastError } from '../../../store/toastStore';
import { useUiStore, type MapModal as MapModalState } from '../../../store/uiStore';
import { useCreateMap, useJoinMap, useLeaveMap } from '../queries';

/** 지도 모달(#map-modal): 공유 지도 만들기 → 만든 결과(초대코드·3줄 규칙) / 초대코드 합류 / 탈퇴 경고 */
export function MapModal() {
  const modal = useUiStore(state => state.mapModal);
  const close = useUiStore(state => state.closeMapModal);
  return (
    <Modal id="map-modal" boxId="map-modal-body" open={!!modal} onClose={close}>
      {modal ? <MapModalBody modal={modal} close={close} /> : null}
    </Modal>
  );
}

function MapModalBody({ modal, close }: { modal: MapModalState; close: () => void }) {
  const nameRef = useRef<HTMLInputElement>(null);
  const codeRef = useRef<HTMLInputElement>(null);
  const openMapModal = useUiStore(state => state.openMapModal);
  const createMap = useCreateMap();
  const joinMap = useJoinMap();
  const leaveMap = useLeaveMap();

  if (modal.kind === 'create') {
    const create = async () => {
      try {
        const created = await createMap.mutateAsync((nameRef.current?.value ?? '').trim());
        openMapModal({ kind: 'created', name: created.name, inviteCode: created.inviteCode, rules: created.rules });
      } catch (error) {
        toastError(error);
      }
    };
    return (
      <>
        <h3 style={{ margin: 0 }}>공유 지도 만들기</h3>
        <div className="row"><input type="text" id="mm-name" ref={nameRef} maxLength={40} placeholder="지도 이름 (예: 부산 원정대)" aria-label="지도 이름" /></div>
        <div className="row"><button className="btn" id="mm-cancel" onClick={close}>취소</button><button className="btn primary" id="mm-create" onClick={() => void create()}>만들기</button></div>
      </>
    );
  }
  if (modal.kind === 'created') {
    return (
      <>
        <h3 style={{ margin: 0 }}>「{modal.name}」를 만들었어요</h3>
        <p>초대코드 <b id="mm-invite">{modal.inviteCode}</b> 를 친구에게 알려 주세요(최대 4명).</p>
        <ol className="rules" id="mm-rules">{modal.rules.map(line => <li key={line}>{line}</li>)}</ol>
        <div className="row"><button className="btn primary" id="mm-done" onClick={close}>확인</button></div>
      </>
    );
  }
  if (modal.kind === 'join') {
    const join = async () => {
      try {
        const joined = await joinMap.mutateAsync((codeRef.current?.value ?? '').trim());
        close();
        toast('✓', joined.rejoined ? `「${joined.name}」에 돌아왔어요` : `「${joined.name}」에 합류`,
          joined.rejoined ? '숨겨졌던 내 영토가 복구돼요(선점은 돌아오지 않아요)' : joined.rules[0]);
      } catch (error) {
        toastError(error);
      }
    };
    return (
      <>
        <h3 style={{ margin: 0 }}>초대코드로 합류</h3>
        <div className="row"><input type="text" id="mm-code" ref={codeRef} maxLength={8} placeholder="8자 초대코드" aria-label="초대코드" /></div>
        <div className="row"><button className="btn" id="mm-cancel" onClick={close}>취소</button><button className="btn primary" id="mm-join" onClick={() => void join()}>합류</button></div>
      </>
    );
  }
  const leave = async () => {
    try {
      const left = await leaveMap.mutateAsync(modal.mapId);
      close();
      toast('✓', '지도에서 탈퇴', left.message);
    } catch (error) {
      toastError(error);
    }
  };
  return (
    <>
      <h3 style={{ margin: 0 }}>「{modal.name}」에서 탈퇴</h3>
      <p id="leave-text">내 영토 {modal.regionCount}곳이 지도에서 사라집니다. 내가 선점한 지역은 다음에 칠한 멤버에게 넘어가요. 7일 안에 초대코드로 돌아오면 복구돼요(선점은 돌아오지 않아요). 그사이 자리가 차면(최대 4명) 다시 합류할 수 없어요.</p>
      <div className="row"><button className="btn" id="mm-cancel" onClick={close}>취소</button><button className="btn danger" id="leave-ok" onClick={() => void leave()}>탈퇴</button></div>
    </>
  );
}
