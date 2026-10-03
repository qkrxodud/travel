/** 진행 막대(.bar > i) */
export function Bar({ percent, gold = false, id }: { percent: number; gold?: boolean; id?: string }) {
  return (
    <div className="bar"><i id={id} className={gold ? 'gold' : undefined} style={{ width: `${percent}%` }} /></div>
  );
}
