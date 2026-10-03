/** 캐릭터 오라·재킷 그라데이션 정의(프로토타입 상단 숨은 SVG 그대로) */
export function SvgDefs() {
  return (
    <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden="true">
      <defs>
        <radialGradient id="auraGrad"><stop offset="0" stopColor="#ffd166" stopOpacity="1" /><stop offset=".5" stopColor="#b48af0" stopOpacity=".6" /><stop offset="1" stopColor="#b48af0" stopOpacity="0" /></radialGradient>
        <linearGradient id="auraRing" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#ffd166" /><stop offset="1" stopColor="#b48af0" /></linearGradient>
        <linearGradient id="jacket" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#2fc3ad" /><stop offset="1" stopColor="#0b6b5f" /></linearGradient>
        <radialGradient id="skin" cx=".4" cy=".35" r=".8"><stop offset="0" stopColor="#f7dcc3" /><stop offset="1" stopColor="#d9a98a" /></radialGradient>
        <radialGradient id="rim" cx=".5" cy=".5" r=".55"><stop offset=".75" stopColor="#000" stopOpacity="0" /><stop offset="1" stopColor="#000" stopOpacity=".18" /></radialGradient>
        <radialGradient id="tokGrad" cx=".35" cy=".3" r=".8"><stop offset="0" stopColor="#ffffff" /><stop offset="1" stopColor="#dfe6ee" /></radialGradient>
        <filter id="soft" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="4" /></filter>
      </defs>
    </svg>
  );
}
