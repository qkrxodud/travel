import { line, scaleLinear } from 'd3';
import { useState, type PointerEvent } from 'react';
import { countText, DAY_STATUS_TEXT, gapBands, lastValue, niceMax, shortDay, spreadLabels, type Trend } from '../model/metrics';

const WIDTH = 640;
const HEIGHT = 220;
const MARGIN = { top: 12, right: 72, bottom: 26, left: 40 };
const TICKS = 4;

/**
 * 일별 추이 선 차트(SVG, d3 스케일·선 생성기만 — 그리기는 React). 한 축, 고정 색 순서(series-1..3), 범례 + 선 끝 이름표,
 * 가리키면 그날 세로선과 값 상자. 셀 수 없는 날(배치 전·보관 기간 지남)은 선을 끊고 빗금 띠로 구분한다(0 으로 그리지 않는다).
 * 숫자 표는 DailyTable.
 */
export function TrendChart({ id, trend }: { id: string; trend: Trend }) {
  const [hover, setHover] = useState<number | null>(null);
  const count = trend.days.length;
  const top = niceMax(trend.series.flatMap(series => series.values));
  const xScale = scaleLinear().domain([0, Math.max(1, count - 1)]).range([MARGIN.left, WIDTH - MARGIN.right]);
  const yScale = scaleLinear().domain([0, top]).range([HEIGHT - MARGIN.bottom, MARGIN.top]);
  const path = line<number | null>()
    .defined(value => value !== null)
    .x((_value, index) => xScale(index))
    .y(value => yScale(value ?? 0));
  const ticks = yScale.ticks(TICKS);
  const labelEvery = Math.max(1, Math.ceil(count / 6));
  const ends = trend.series.map(series => lastValue(series.values));
  const endLabels = spreadLabels(ends.map(end => yScale(end?.value ?? 0)), 14);
  const step = count > 1 ? (WIDTH - MARGIN.left - MARGIN.right) / (count - 1) : 0;
  const bands = gapBands(trend.status);
  const gapKinds = [...new Set(bands.map(band => band.status))];

  const onMove = (event: PointerEvent<SVGRectElement>) => {
    const box = event.currentTarget.ownerSVGElement?.getBoundingClientRect();
    if (!box || !count) return;
    const pointerX = ((event.clientX - box.left) / box.width) * WIDTH;
    setHover(Math.min(count - 1, Math.max(0, Math.round(xScale.invert(pointerX)))));
  };
  const hoverX = hover === null ? 0 : xScale(hover);
  const hoverStatus = hover === null ? 'counted' : trend.status[hover] ?? 'counted';

  return (
    <figure className="trend" id={id}>
      <ul className="legend">
        {trend.series.map(series => <li key={series.key} className={`s${series.slot}`}>{series.label}</li>)}
        {gapKinds.map(kind => <li key={kind} className={`gap ${kind}`}>{DAY_STATUS_TEXT[kind]}</li>)}
      </ul>
      <div className="trend-plot">
        <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={`${trend.series.map(series => series.label).join('·')} 일별 추이`}>
          {bands.map(band => (
            <rect key={`${band.status}-${band.start}`} className={`gap ${band.status}`} data-gap={band.status}
              x={Math.max(MARGIN.left, xScale(band.start) - step / 2)} y={MARGIN.top}
              width={Math.min(WIDTH - MARGIN.right, xScale(band.end) + step / 2) - Math.max(MARGIN.left, xScale(band.start) - step / 2)}
              height={HEIGHT - MARGIN.top - MARGIN.bottom}>
              <title>{`${DAY_STATUS_TEXT[band.status]} ${trend.days[band.start]} ~ ${trend.days[band.end]}`}</title>
            </rect>
          ))}
          <g className="grid">
            {ticks.map(tick => (
              <g key={tick}>
                <line x1={MARGIN.left} x2={WIDTH - MARGIN.right} y1={yScale(tick)} y2={yScale(tick)} />
                <text x={MARGIN.left - 6} y={yScale(tick)} dy="0.32em" textAnchor="end">{tick.toLocaleString('ko-KR')}</text>
              </g>
            ))}
            {trend.days.map((day, index) => (index % labelEvery === 0 || index === count - 1 ? (
              <text key={day} x={xScale(index)} y={HEIGHT - 8} textAnchor="middle">{shortDay(day)}</text>
            ) : null))}
          </g>
          {trend.series.map((series, seriesIndex) => {
            const end = ends[seriesIndex];
            return (
              <g key={series.key} className={`series s${series.slot}`} data-series={series.key}>
                <path d={path(series.values) ?? ''} />
                {end ? (
                  <text className="end" x={xScale(count - 1) + 8} y={endLabels[seriesIndex]} dy="0.32em">
                    {series.label} {countText(end.value)}
                  </text>
                ) : null}
              </g>
            );
          })}
          {hover !== null ? (
            <g className="cross">
              <line x1={hoverX} x2={hoverX} y1={MARGIN.top} y2={HEIGHT - MARGIN.bottom} />
              {trend.series.map(series => {
                const value = series.values[hover];
                return value === null || value === undefined ? null : <circle key={series.key} className={`s${series.slot}`} cx={hoverX} cy={yScale(value)} r={4} />;
              })}
            </g>
          ) : null}
          <rect className="hit" x={MARGIN.left} y={0} width={WIDTH - MARGIN.left - MARGIN.right} height={HEIGHT}
            onPointerMove={onMove} onPointerLeave={() => setHover(null)} />
        </svg>
        {hover !== null ? (
          <div className="trend-tip" style={{ left: `${(hoverX / WIDTH) * 100}%` }}>
            <b>{trend.days[hover]}</b>
            {hoverStatus === 'counted'
              ? trend.series.map(series => (
                <span key={series.key} className={`s${series.slot}`}>{series.label} <b>{countText(series.values[hover] ?? 0)}</b></span>
              ))
              : <em>{DAY_STATUS_TEXT[hoverStatus]} — 숫자 없음</em>}
          </div>
        ) : null}
      </div>
    </figure>
  );
}
