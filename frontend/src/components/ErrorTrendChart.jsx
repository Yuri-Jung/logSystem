import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  ReferenceLine,
} from 'recharts';

/** ISO-8601 문자열 → "HH:mm" 형식 */
function formatHour(isoStr) {
  const d = new Date(isoStr);
  return `${String(d.getHours()).padStart(2, '0')}:00`;
}

/** ISO-8601 문자열 → "MM/DD HH:mm" 형식 */
function formatFull(isoStr) {
  const d = new Date(isoStr);
  const mm  = String(d.getMonth() + 1).padStart(2, '0');
  const dd  = String(d.getDate()).padStart(2, '0');
  const hh  = String(d.getHours()).padStart(2, '0');
  return `${mm}/${dd} ${hh}:00`;
}

/** Recharts 커스텀 툴팁 */
function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-white border border-gray-200 rounded-lg shadow-lg px-4 py-3 text-sm">
      <p className="font-semibold text-gray-700 mb-1">{formatFull(label)}</p>
      <p className="text-red-500 font-bold">
        에러 {payload[0].value.toLocaleString()}건
      </p>
    </div>
  );
}

/**
 * 시간대별 에러 발생 추이 라인 차트.
 *
 * @param {Array}   data    [{hour: ISO문자열, errorCount: number}]
 * @param {boolean} loading 로딩 상태
 * @param {number}  hours   조회 시간 범위 (제목 표시용)
 */
export function ErrorTrendChart({ data, loading, hours = 24 }) {
  if (loading && !data.length) {
    return (
      <div className="h-72 flex items-center justify-center">
        <div className="w-8 h-8 border-4 border-blue-200 border-t-blue-500 rounded-full animate-spin" />
      </div>
    );
  }

  if (!loading && !data.length) {
    return (
      <div className="h-72 flex items-center justify-center text-gray-400 text-sm">
        해당 기간에 에러 로그가 없습니다.
      </div>
    );
  }

  // 평균 에러 수 — ReferenceLine으로 시각화
  const avg = data.reduce((s, d) => s + d.errorCount, 0) / data.length;

  return (
    <ResponsiveContainer width="100%" height={288}>
      <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />

        <XAxis
          dataKey="hour"
          tickFormatter={formatHour}
          tick={{ fontSize: 11, fill: '#9ca3af' }}
          tickLine={false}
          axisLine={{ stroke: '#e5e7eb' }}
          // 데이터가 많으면 간격 조절
          interval={Math.max(0, Math.floor(data.length / 8) - 1)}
        />

        <YAxis
          allowDecimals={false}
          tick={{ fontSize: 11, fill: '#9ca3af' }}
          tickLine={false}
          axisLine={false}
          width={36}
        />

        <Tooltip content={<CustomTooltip />} />

        {/* 평균선 */}
        <ReferenceLine
          y={avg}
          stroke="#f59e0b"
          strokeDasharray="4 4"
          label={{ value: '평균', position: 'right', fontSize: 10, fill: '#f59e0b' }}
        />

        <Line
          type="monotone"
          dataKey="errorCount"
          name="에러 발생 수"
          stroke="#ef4444"
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4, strokeWidth: 0 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
