import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { SceneRequest, SceneResponse } from '../../api/types/wardrobe';
import { wardrobeApi } from '../../api/wardrobe';
import { wardrobeKeys } from '../../shared/queries/wardrobe';

/** 착용·해제·장식·성별(PUT /scene) — 응답 장면을 바로 쓰고 가방(착용 표시)을 다시 읽는다. */
export function useEditScene() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SceneRequest) => wardrobeApi.editScene(body),
    onSuccess: async scene => {
      queryClient.setQueryData<SceneResponse>(wardrobeKeys.scene(), scene);
      await queryClient.invalidateQueries({ queryKey: wardrobeKeys.inventory() });
    },
  });
}

export function useFavorite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, favorite }: { itemId: string; favorite: boolean }) => wardrobeApi.favorite(itemId, favorite),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: wardrobeKeys.inventory() }),
  });
}
