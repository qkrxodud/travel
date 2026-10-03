/** handle 입력 정리(앞 @·공백 제거) — 랭킹(팔로우·비교)과 프로필(handle 바꾸기)이 함께 쓴다. */
export const normalizeHandle = (input: string | null | undefined): string => String(input || '').trim().replace(/^@/, '');
