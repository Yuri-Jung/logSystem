import { useState, useCallback } from 'react';
import {
  LineChart, Line,
  BarChart, Bar,
  XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer, Cell,
} from 'recharts';
import { usePolling } from '../hooks/usePolling';
import { fetchRecentStats, fetchLogTrend, fetchTopExceptions } from '../services/api';

const POLL_INTERVAL_MS = 30_000;
const HOUR_OPTIONS     = [6, 12, 24, 48];
const EXCEPTION_COLORS = ['#ef4444', '#f97316', '#eab308', '#22c55e', '#3b82f6'];
const CHART_THEME = {
  grid:    '#1f2937',
  axis:    '#6b7280',
  tooltip: { backgroundColor: '#111827', border: '1px solid #374151', color: '#f9fafb' },
};

// ──────────────────────────────────────────────────────────────────────────────
// 서브 컴포넌트
// ──────────────────────────────────────────────────────────────────────────────

function MetricCard({ title, value, unit, sub, colorClass = 'text-white', loading }) {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
      <p className="text-xs font-medium text-gray-400 uppercase tracking-wider">{title}</p>
      {loading ? (
        <div className="mt-3 h-9 w-28 bg-gray-800 rounded animate-pulse" />
      ) : (
        <p className={`mt-2 text-3xl font-bold tabular-nums ${colorClass}`}>
          {value ?? '—'}
          {unit && <span className="ml-1 text-sm font-normal text-gray-500">{unit}</span>}
        </p>
      )}
      {sub && <p className="mt-1 text-xs text-gray-600">{sub}</p>}
    </div>
  );
}

function SectionTitle({ children }) {
  return (
    <h2 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-4">
      {children}
    </h2>
  );
}

// ──────────────────────────────────────────────────────────────────────────────
// 커스텀 툴팁
// ──────────────────────────────────────────────────────────────────────────────

function TrendTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-lg p-3 text-xs shadow-xl">
      <p className="text-gray-400 mb-2">{label}</p>
      {payload.map(p => (
        <p key={p.name} style={{ color: p.color }} className="leading-5">
          {p.name}: <span className="font-bold">{p.value.toLocaleString()}</span>
        </p>
      ))}
    </div>
  );
}

function ExceptionTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const { fullName, count } = payload[0]?.payload ?? {};
  return (
    <div className="bg-gray-900 border border-gray-700 rounded-lg p-3 text-xs shadow-xl max-w-xs">
      <p className="text-gray-300 break-all mb-1">{fullName}</p>
      <p className="text-white font-bold">{count?.toLocaleString()} 건</p>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────────────────────
// 메인 대시보드
// ──────────────────────────────────────────────────────────────────────────────

export function Dashboard() {
  const [stats,      setStats]      = useState(null);
  const [trend,      setTrend]      = useState([]);
  const [exceptions, setExceptions] = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState(null);
  const [hours,      setHours]      = useState(24);
  const [lastUpdate, setLastUpdate] = useState(null);

  const refresh = useCallback(async () => {
    const CALLS = [
      { label: '/analysis/recent-stats',  fn: fetchRecentStats      },
      { label: '/analysis/log-trend',     fn: () => fetchLogTrend(hours) },
      { label: '/analysis/top-exceptions', fn: () => fetchTopExceptions(5) },
    ];

    const results = await Promise.allSettled(CALLS.map(c => c.fn()));

    const failed = results
      .map((r, i) => r.status === 'rejected' ? CALLS[i].label : null)
      .filter(Boolean);

    if (failed.length) {
      const firstErr = results.find(r => r.status === 'rejected').reason;
      const status   = firstErr?.response?.status;
      const hint     = status === 404 ? '(백엔드 재시작 필요)' : status >= 500 ? '(서버 오류)' : '(네트워크 오류)';
      setError(`요청 실패 ${hint} — ${failed.join(', ')}`);
      console.group('[Dashboard] API 오류');
      results.forEach((r, i) => {
        if (r.status === 'rejected') {
          console.error(`${CALLS[i].label}:`, r.reason?.response?.status, r.reason?.message);
        }
      });
      console.groupEnd();
    } else {
      setError(null);
    }

    const [statsRes, trendRes, exceptionsRes] = results;

    if (statsRes.status === 'fulfilled') {
      setStats(statsRes.value);
    }
    if (trendRes.status === 'fulfilled') {
      setTrend(trendRes.value.map(p => ({
        time: new Date(p.hour).toLocaleTimeString('ko-KR', {
          hour: '2-digit', minute: '2-digit', hour12: false,
        }),
        API:   p.apiCount,
        DB:    p.dbCount,
        Error: p.errorCount,
      })));
    }
    if (exceptionsRes.status === 'fulfilled') {
      setExceptions(exceptionsRes.value.map(e => ({
        name:     e.exceptionClass.split('.').pop(),
        fullName: e.exceptionClass,
        count:    e.count,
      })));
    }

    if (!failed.length) setLastUpdate(new Date());
    setLoading(false);
  }, [hours]);

  usePolling(refresh, POLL_INTERVAL_MS);

  const avgMs = stats?.avgApiResponseTimeMs;
  const p95Ms = stats?.p95ApiResponseTimeMs;
  const errCnt = stats?.errorCount;

  const responseColor = (ms) => {
    if (ms == null) return 'text-white';
    if (ms > 1000) return 'text-red-400';
    if (ms > 500)  return 'text-yellow-400';
    return 'text-emerald-400';
  };

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100 font-sans">

      {/* ── 헤더 ── */}
      <header className="border-b border-gray-800 px-6 py-4 flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-white tracking-tight">
            로그 모니터링 대시보드
          </h1>
          {lastUpdate && (
            <p className="text-xs text-gray-600 mt-0.5">
              마지막 갱신: {lastUpdate.toLocaleTimeString('ko-KR')}
              <span className="ml-2">· 30초 자동 갱신</span>
            </p>
          )}
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`w-2 h-2 rounded-full ${error ? 'bg-red-500' : 'bg-emerald-400 animate-pulse'}`}
          />
          <span className="text-xs text-gray-500">
            {error ? '연결 오류' : '실시간 모니터링'}
          </span>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-6 py-8 space-y-10">

        {/* ── 오류 배너 ── */}
        {error && (
          <div className="bg-red-950 border border-red-800 text-red-300 rounded-lg px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {/* ── 지표 카드 3개 ── */}
        <section>
          <SectionTitle>최근 1시간 요약</SectionTitle>
          <div className="grid grid-cols-3 gap-4">
            <MetricCard
              title="API 평균 응답 시간"
              value={avgMs != null ? avgMs.toFixed(1) : null}
              unit="ms"
              colorClass={responseColor(avgMs)}
              loading={loading}
            />
            <MetricCard
              title="P95 응답 시간"
              sub="상위 5% 응답 속도 기준"
              value={p95Ms != null ? p95Ms.toFixed(1) : null}
              unit="ms"
              colorClass={responseColor(p95Ms)}
              loading={loading}
            />
            <MetricCard
              title="에러 발생 건수"
              value={errCnt != null ? errCnt.toLocaleString() : null}
              unit="건"
              colorClass={errCnt > 0 ? 'text-red-400' : 'text-emerald-400'}
              loading={loading}
            />
          </div>
        </section>

        {/* ── 시간별 로그 추이 (멀티라인 차트) ── */}
        <section className="bg-gray-900 border border-gray-800 rounded-xl p-6">
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-sm font-semibold text-white">시간별 로그 발생 추이</h2>
              <p className="text-xs text-gray-500 mt-0.5">API / DB / Error · 1시간 단위 집계</p>
            </div>
            <div className="flex gap-1">
              {HOUR_OPTIONS.map(h => (
                <button
                  key={h}
                  onClick={() => setHours(h)}
                  className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                    hours === h
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
                  }`}
                >
                  {h >= 48 ? '2일' : `${h}h`}
                </button>
              ))}
            </div>
          </div>

          {loading ? (
            <div className="h-64 bg-gray-800 rounded animate-pulse" />
          ) : trend.length === 0 ? (
            <div className="h-64 flex items-center justify-center text-gray-600 text-sm">
              조회된 데이터가 없습니다
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <LineChart data={trend} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={CHART_THEME.grid} vertical={false} />
                <XAxis
                  dataKey="time"
                  stroke={CHART_THEME.axis}
                  tick={{ fill: CHART_THEME.axis, fontSize: 11 }}
                  tickLine={false}
                  interval="preserveStartEnd"
                />
                <YAxis
                  stroke={CHART_THEME.axis}
                  tick={{ fill: CHART_THEME.axis, fontSize: 11 }}
                  tickLine={false}
                  axisLine={false}
                  allowDecimals={false}
                />
                <Tooltip content={<TrendTooltip />} />
                <Legend
                  iconType="circle"
                  iconSize={8}
                  wrapperStyle={{ fontSize: 12, paddingTop: 16 }}
                />
                <Line
                  type="monotone" dataKey="API"
                  stroke="#3b82f6" strokeWidth={2} dot={false} activeDot={{ r: 4 }}
                />
                <Line
                  type="monotone" dataKey="DB"
                  stroke="#f59e0b" strokeWidth={2} dot={false} activeDot={{ r: 4 }}
                />
                <Line
                  type="monotone" dataKey="Error"
                  stroke="#ef4444" strokeWidth={2} dot={false} activeDot={{ r: 4 }}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </section>

        {/* ── TOP 5 Exception ── */}
        <section className="bg-gray-900 border border-gray-800 rounded-xl p-6">
          <div className="mb-6">
            <h2 className="text-sm font-semibold text-white">에러 TOP 5 — Exception Class</h2>
            <p className="text-xs text-gray-500 mt-0.5">log-error 인덱스 전체 기간 기준</p>
          </div>

          {loading ? (
            <div className="h-48 bg-gray-800 rounded animate-pulse" />
          ) : exceptions.length === 0 ? (
            <div className="h-48 flex items-center justify-center text-gray-600 text-sm">
              에러 데이터가 없습니다
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart
                data={exceptions}
                layout="vertical"
                margin={{ top: 0, right: 24, bottom: 0, left: 0 }}
              >
                <CartesianGrid
                  strokeDasharray="3 3"
                  stroke={CHART_THEME.grid}
                  horizontal={false}
                />
                <XAxis
                  type="number"
                  stroke={CHART_THEME.axis}
                  tick={{ fill: CHART_THEME.axis, fontSize: 11 }}
                  tickLine={false}
                  allowDecimals={false}
                />
                <YAxis
                  type="category"
                  dataKey="name"
                  width={160}
                  stroke={CHART_THEME.axis}
                  tick={{ fill: '#9ca3af', fontSize: 12 }}
                  tickLine={false}
                  axisLine={false}
                />
                <Tooltip content={<ExceptionTooltip />} />
                <Bar dataKey="count" radius={[0, 4, 4, 0]} maxBarSize={28}>
                  {exceptions.map((_, i) => (
                    <Cell key={i} fill={EXCEPTION_COLORS[i % EXCEPTION_COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </section>

      </main>
    </div>
  );
}
