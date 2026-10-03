import type { MouseEvent, ReactNode } from 'react';

interface ModalProps {
  id: string;
  open: boolean;
  onClose: () => void;
  /** .box 의 id(프로토타입: map-modal-body · checkin-body) */
  boxId?: string;
  children?: ReactNode;
}

/** 어두운 배경 + 가운데 상자. 배경을 누르면 닫힌다. 닫혀 있어도 DOM 은 남는다(hidden — 프로토타입·E2E 와 같게). */
export function Modal({ id, open, onClose, boxId, children }: ModalProps) {
  const onBackdrop = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) onClose();
  };
  return (
    <div className="modal" id={id} hidden={!open} onClick={onBackdrop}>
      <div className="box" id={boxId}>{open ? children : null}</div>
    </div>
  );
}
