import { useUiStore } from '../../store/uiStore';


/** 자랑 카드 미리보기(#modal) — 서버 PNG·화면에서 그린 카드 공용 */
export function CardModal() {
  const cardUrl = useUiStore(state => state.cardUrl);
  const closeCard = useUiStore(state => state.closeCard);
  return (
    <div className="modal" id="modal" hidden={!cardUrl} onClick={event => { if (event.target === event.currentTarget) closeCard(); }}>
      <div className="box">
        <img id="share-img" alt="자랑 카드 미리보기" src={cardUrl ?? undefined} />
        <p>실제 서비스에서는 이 이미지가 공개 프로필 링크의 미리보기(OG 이미지)로 쓰입니다. 카톡·인스타에 링크만 던지면 카드가 뜨는 구조예요.</p>
        <div className="row"><button className="btn primary" id="t-close" onClick={closeCard}>닫기</button></div>
      </div>
    </div>
  );
}

