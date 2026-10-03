/** POST /dev/seed (local 전용) */
export interface SeedResponse {
  seeded: number;
}

/** DELETE /dev/visits (local 전용) */
export interface ClearResponse {
  cleared: number;
}
