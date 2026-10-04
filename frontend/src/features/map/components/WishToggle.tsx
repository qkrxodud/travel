import { toServerCode, type ApiError } from '../../../api/client';
import { useWishlist } from '../../../shared/queries/wishlist';
import { toast, toastError } from '../../../store/toastStore';
import { pinErrorText, wishToggle } from '../model/wishlist';
import { usePin, useRevisitStatus, useUnpin } from '../queries';

/** 지역 상세 "가고 싶어요" 토글(#d-wish) — 칠한 곳(탐험가 단위)·상한이면 비활성과 이유, 꽂은 곳은 다시 누르면 뺀다 */
export function WishToggle({ code, name }: { code: string; name: string }) {
  const { data: wishlist } = useWishlist();
  const { data: status } = useRevisitStatus(code);
  const pin = usePin();
  const unpin = useUnpin();
  if (!wishlist || !status) return null;
  const view = wishToggle(toServerCode(code), wishlist, status.painted);
  const press = async () => {
    try {
      if (view.state === 'pinned') {
        await unpin.mutateAsync(code);
        toast('☆', '가고 싶은 곳에서 뺐어요', name);
      } else {
        await pin.mutateAsync(code);
        toast('📍', '가고 싶은 곳에 꽂았어요', `${name} · 칠하면 +${wishlist.xpPerWish} XP`);
      }
    } catch (error) {
      toastError(error, pinErrorText((error as Partial<ApiError>).code, wishlist.max) ?? undefined);
    }
  };
  return (
    <div className="wish">
      <button
        className={`btn wish-toggle ${view.pressed ? 'on' : ''}`}
        id="d-wish"
        aria-pressed={view.pressed}
        data-state={view.state}
        disabled={view.disabled || pin.isPending || unpin.isPending}
        onClick={() => void press()}
      >
        {view.label}
      </button>
      {view.hint ? <p className="why" id="d-wish-why">{view.hint}</p> : null}
    </div>
  );
}
