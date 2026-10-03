import { useRef } from 'react';
import { toast, toastError } from '../../../store/toastStore';
import { useChangeHandle, useLoginIntent, useLogout } from '../queries';
import { useSession } from '../../../shared/queries/session';
import { noticeText } from '../model/account';

/** 계정(4단계): 구글 로그인 · handle · 병합 안내 · 로그아웃 */
export function AccountCard() {
  const { data: session } = useSession();
  const changeHandle = useChangeHandle();
  const loginIntent = useLoginIntent();
  const logout = useLogout();
  const inputRef = useRef<HTMLInputElement>(null);
  const notice = noticeText(session?.mergeNotice);

  const rename = async () => {
    try {
      const changed = await changeHandle.mutateAsync(inputRef.current?.value ?? '');
      toast('✓', 'handle 변경', '@' + changed.handle);
    } catch (error) {
      toastError(error);
    }
  };
  const signOut = async () => {
    try {
      await logout.mutateAsync();
      toast('✓', '로그아웃', '이 기기는 새 익명 탐험가로 시작해요');
    } catch (error) {
      toastError(error);
    }
  };
  const signIn = async () => {
    try {
      const intent = await loginIntent.mutateAsync();
      if (intent.loginUrl) location.href = intent.loginUrl;
    } catch (error) {
      toastError(error);
    }
  };

  let body;
  if (session?.loggedIn) {
    body = (
      <>
        <p className="who" id="acct-who"><b id="acct-handle">{`@${session.handle}`}</b>{` · ${session.email || ''}`}</p>
        {notice ? <div className="merged" id="acct-notice">{notice}</div> : null}
        <div className="handle-row">
          <input id="acct-handle-input" ref={inputRef} key={session.handle} defaultValue={session.handle ?? ''} maxLength={20} aria-label="새 handle" />
          <button className="btn sm" id="t-handle" onClick={() => void rename()}>handle 바꾸기</button>
        </div>
        <div className="row"><button className="btn sm" id="t-logout" onClick={() => void signOut()}>로그아웃</button></div>
      </>
    );
  } else if (session !== undefined) {
    const enabled = !!session?.googleLoginEnabled;
    body = (
      <>
        <button className="btn primary" id="t-google" disabled={!enabled} onClick={() => void signIn()}>구글로 로그인</button>
        <p className="note" id="acct-note">
          {enabled ? '로그인하면 지금 익명으로 칠한 영토가 계정으로 옮겨져요(이미 계정이 있으면 합쳐져요).'
            : '구글 로그인은 아직 준비 중이에요(서버에 구글 클라이언트 설정이 없음). 익명 탐험은 그대로 쓸 수 있어요.'}
        </p>
      </>
    );
  }
  return (
    <div className="card acct" id="account-card">
      <h2>계정 <span id="acct-sub">로그인하면 기기를 바꿔도 영토가 이어져요</span></h2>
      <div id="acct-body">{body}</div>
    </div>
  );
}
