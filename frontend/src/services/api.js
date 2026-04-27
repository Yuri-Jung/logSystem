import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 10_000,
});

/** 최근 1시간 집계 요약 (avgApiResponseTimeMs, errorCount) */
export const fetchRecentStats = () =>
  api.get('/analysis/recent-stats').then(r => r.data);

/**
 * 시간대별 에러 발생 추이 (1시간 단위 버킷)
 * @param {number} hours 조회 범위 (기본 24시간)
 */
export const fetchErrorTrend = (hours = 24) =>
  api.get('/analysis/error-trend', { params: { hours } }).then(r => r.data);

/**
 * 로그 조건 검색
 * @param {Object} params level, logType, from, to, page, size, searchAfter
 */
export const fetchLogs = (params = {}) =>
  api.get('/logs/search', { params }).then(r => r.data);

/**
 * traceId 기반 전체 흐름 조회
 * @param {string} traceId
 */
export const fetchTrace = (traceId) =>
  api.get(`/logs/trace/${traceId}`).then(r => r.data);

export default api;
