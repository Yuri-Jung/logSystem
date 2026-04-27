import { useState, useCallback } from 'react';
import { ErrorTrendChart } from './ErrorTrendChart';
import { StatCard } from './StatCard';
import { usePolling } from '../hooks/usePolling';
import { fetchRecentStats, fetchErrorTrend } from '../services/api';

const POLL_INTERVAL_MS = 30_000; // 30초 폴링
const HOUR_OPTIONS = [6, 12, 24, 48, 168];

/**
 * 로그 분석 메인 대시보드.
 *
 * 폴링 전략:
 * - 30초 간격으로 /api/analysis/recent-stats 와 /api/analysis/error-trend 를 동시 호출한다.
 * - 두 요청은 Promise.all 로 병렬 실행하여 왕복 횟수를 최소화한다.
 * - 컴포넌트 언마운트 시 자동으로 폴링이 중지된다.
 *
 * 업그레이드 경로 (WebSocket/SSE):
 * - usePolling 훅을 제거하고 useEffect 내에서 EventSource(SSE)나
 *   SockJS+STOMP(WebSocket) 연결로 교체하면 서버 push 방식으로 전환된다.
 */
export function LogDashboard() {
  const [stats,      setStats]      = useState(null);
  const [trend,      setTrend]      = useState([]);
  const [loading,    setLoading]    = useState(true);
  const [error,      setError]      = useState(null);
  const [hours,      setHours]      = useState(24);
  const [lastUpdate, setLastUpdate] = useState(null);

  const refresh = useCallback(async () => {
    try {
      const [statsData, trendData] = await Promise.all([
        fetchRecentStats(),
        fetchErrorTrend(hours),
      ]);
      setStats(statsData);
      setTrend(trendData);
      setError(null);
      setLastUpdate(new Date());
    } catch (err) {
      setError('백엔드 연결 실패. 재시도 중...');
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [hours]);

  // 30초마다 자동 갱신 — hours 변경 시 interval 재시작
  usePolling(refresh, POLL_INTERVAL_MS);

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 헤더 */}
      <header className="bg-white border-b border-gray-200 px-6 py-4 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-gray-900">로그 분석 대시보드</h1>
          {lastUpdate && (
            <p className="text-xs text-gray-400 mt-0.5">
              마지막 갱신: {lastUpdate.toLocaleTimeString('ko-KR')}
              <span className="ml-2 text-gray-300">· 30초마다 자동 갱신</span>
            </p>
          )}
        </div>

        {/* 갱신 상태 표시 */}
        <div className="flex items-center gap-2">
          <span
            className={`w-2 h-2 rounded-full ${
              error ? 'bg-red-400' : 'bg-green-400 animate-pulse'
            }`}
          />
          <span className="text-sm text-gray-500">
            {error ? '연결 오류' : '실시간 모니터링'}
          </span>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-8 space-y-8">
        {/* 오류 배너 */}
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 text-sm">
            {error}
          </div>
        )}

        {/* 요약 카드 */}
        <section>
          <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wider mb-3">
            최근 1시간 요약
          </h2>
          <div className="grid grid-cols-2 gap-4">
            <StatCard
              title="API 평균 응답 시간"
              value={
                stats?.avgApiResponseTimeMs != null
                  ? stats.avgApiResponseTimeMs.toFixed(1)
                  : '—'
              }
              unit="ms"
              color={
                stats?.avgApiResponseTimeMs > 1000 ? 'text-red-500' :
                stats?.avgApiResponseTimeMs > 500  ? 'text-yellow-500' :
                'text-green-600'
              }
              loading={loading}
            />
            <StatCard
              title="에러 발생 건수"
              value={stats?.errorCount?.toLocaleString() ?? '—'}
              unit="건"
              color={stats?.errorCount > 0 ? 'text-red-500' : 'text-green-600'}
              loading={loading}
            />
          </div>
        </section>

        {/* 에러 추이 차트 */}
        <section className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-base font-semibold text-gray-900">
                시간대별 에러 발생 추이
              </h2>
              <p className="text-xs text-gray-400 mt-0.5">
                log-error 인덱스 · 1시간 단위 집계
              </p>
            </div>

            {/* 조회 범위 선택 */}
            <div className="flex gap-1">
              {HOUR_OPTIONS.map(h => (
                <button
                  key={h}
                  onClick={() => setHours(h)}
                  className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                    hours === h
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  {h >= 168 ? '7일' : h >= 48 ? '2일' : `${h}h`}
                </button>
              ))}
            </div>
          </div>

          <ErrorTrendChart data={trend} loading={loading} hours={hours} />
        </section>
      </main>
    </div>
  );
}
