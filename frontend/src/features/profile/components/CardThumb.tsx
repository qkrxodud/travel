import { useEffect, useState } from 'react';
import type { CardKind } from '../../../api/types/sharing';
import { useUiStore } from '../../../store/uiStore';
import { useCardImage } from '../queries';

/** 서버 카드 미리보기(blob: URL). 쓰던 URL 은 바뀌거나 사라질 때 놓아 준다. 다 그려지면 data-loaded=true. */
export function CardThumb({ kind, alt, caption, enabled }: { kind: CardKind; alt: string; caption: string; enabled: boolean }) {
  const { data: url, isError } = useCardImage(kind, enabled);
  const showCard = useUiStore(state => state.showCard);
  const [loadedUrl, setLoadedUrl] = useState<string | null>(null);
  useEffect(() => () => {
    if (url) URL.revokeObjectURL(url);
  }, [url]);
  const loaded = !!url && loadedUrl === url;
  return (
    <figure>
      <img
        id={`card-${kind}`}
        data-kind={kind}
        alt={alt}
        src={url}
        data-loaded={isError ? 'error' : loaded ? 'true' : undefined}
        onLoad={() => setLoadedUrl(url ?? null)}
        onClick={() => { if (loaded && url) showCard(url); }}
      />
      <figcaption>{caption}</figcaption>
    </figure>
  );
}
