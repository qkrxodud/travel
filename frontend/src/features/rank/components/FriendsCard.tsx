import { useRef } from 'react';
import type { ApiError } from '../../../api/client';
import type { FriendsResponse } from '../../../api/types/social';
import { toast, toastError } from '../../../store/toastStore';
import { useUiStore } from '../../../store/uiStore';
import { avatarColor, displayHandle, initialOf, RELATION_LABEL, relationOf } from '../model/social';
import { normalizeHandle } from '../../../shared/lib/handle';
import { useFollow, useRefreshSocial, useStartCompare, useUnfollow } from '../queries';

const AVATAR_STYLE = { width: 22, height: 22, borderRadius: '50%', display: 'grid', placeItems: 'center', color: '#fff', fontSize: 11 } as const;

/** 친구(팔로우 — 로그인 필요). 서로 팔로우하면 친구. */
export function FriendsCard({ friends, loggedIn }: { friends: FriendsResponse | null; loggedIn: boolean }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const setTab = useUiStore(state => state.setTab);
  const startCompare = useStartCompare();
  const follow = useFollow();
  const unfollow = useUnfollow();
  const refreshSocial = useRefreshSocial();

  const followHandle = async (input: string | null | undefined) => {
    const handle = normalizeHandle(input);
    if (!handle) return;
    try {
      const followed = await follow.mutateAsync(handle);
      toast('✓', followed.mutual ? '친구가 됐어요' : '팔로우했어요', followed.mutual ? `@${followed.handle}님과 서로 팔로우` : `@${followed.handle}님도 팔로우하면 친구가 돼요`);
      if (inputRef.current) inputRef.current.value = '';
      await refreshSocial();
    } catch (error) {
      // 없는 handle 과 비공개·친구 공개 프로필은 같은 응답(존재 숨김) — 팔로우는 남아 상대가 맞팔하면 친구가 된다
      if ((error as ApiError).code === 'PROFILE_NOT_FOUND') {
        toast('!', '프로필 없음', 'handle 을 확인해 주세요. 비공개·친구 공개 프로필이라면 상대도 나를 팔로우해야 친구가 돼요.');
        if (inputRef.current) inputRef.current.value = '';
      } else toastError(error);
    }
  };
  const unfollowHandle = async (handle: string) => {
    try {
      await unfollow.mutateAsync(handle);
      toast('✓', '언팔로우', '@' + handle);
      if (useUiStore.getState().compareHandle === handle) useUiStore.getState().compareWith(null);
      await refreshSocial();
    } catch (error) {
      toastError(error);
    }
  };

  const people = friends?.people ?? [];
  return (
    <div className="card" id="friends-card">
      <h2>친구 <span id="friends-sub">{friends && loggedIn ? `친구 ${friends.mutualCount}명 · 서로 팔로우하면 친구` : 'handle 로 팔로우 — 서로 팔로우하면 친구'}</span></h2>
      <div className="note" id="friends-login" hidden={loggedIn}>로그인하면 친구를 팔로우하고 친구 랭킹·소식을 볼 수 있어요. <button className="btn sm primary" id="t-rank-login" onClick={() => setTab('profile')}>로그인하러 가기</button></div>
      <div className="social-row" id="follow-row" hidden={!loggedIn}>
        <input id="follow-handle" ref={inputRef} placeholder="@handle" maxLength={21} aria-label="팔로우할 handle" onKeyDown={event => { if (event.key === 'Enter') void followHandle(inputRef.current?.value); }} />
        <button className="btn sm primary" id="t-follow" onClick={() => void followHandle(inputRef.current?.value)}>팔로우</button>
      </div>
      <ul className="friends" id="friends">
        {!loggedIn ? null : people.length === 0 ? <li className="empty">아직 팔로우한 사람이 없어요. 친구의 handle 을 입력해 보세요.</li> : people.map(person => {
          const relation = relationOf(person);
          return (
            <li key={person.handle ?? relation} data-handle={person.handle || ''} data-relation={relation}>
              <span className="av" style={{ ...AVATAR_STYLE, background: avatarColor(person.handle) }}>{initialOf(person.handle)}</span>
              <b>{displayHandle(person.handle)}</b>
              <span className={`tag ${person.mutual ? 'mutual' : ''}`}>{RELATION_LABEL[relation]}</span>
              <span className="sp" />
              {person.handle ? <button className="btn sm" data-compare={person.handle} onClick={() => void startCompare(person.handle)}>비교</button> : null}
              {person.following
                ? <button className="btn sm" data-unfollow={person.handle ?? ''} onClick={() => { if (person.handle) void unfollowHandle(person.handle); }}>언팔로우</button>
                : person.handle ? <button className="btn sm primary" data-follow={person.handle} onClick={() => void followHandle(person.handle)}>맞팔로우</button> : null}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
