import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 10_000,
});

/** 최근 1시간 집계 요약 (avgApiResponseTimeMs, p95ApiResponseTimeMs, errorCount) */
export const fetchRecentStats = () =>
  api.get('/analysis/recent-stats').then(r => r.data);

/**
 * 시간대별 에러 발생 추이 (1시간 단위 버킷)
 * @param {number} hours 조회 범위 (기본 24시간)
 */
export const fetchErrorTrend = (hours = 24) =>
  api.get('/analysis/error-trend', { params: { hours } }).then(r => r.data);

/**
 * 시간대별 API·DB·Error 로그 발생 추이 (멀티라인 차트용)
 * @param {number} hours 조회 범위 (기본 24시간)
 */
export const fetchLogTrend = (hours = 24) =>
  api.get('/analysis/log-trend', { params: { hours } }).then(r => r.data);

/**
 * 발생 빈도 상위 N개 예외 클래스 통계 (수평 바 차트용)
 * @param {number} limit 최대 항목 수 (기본 5)
 */
export const fetchTopExceptions = (limit = 5) =>
  api.get('/analysis/top-exceptions', { params: { limit } }).then(r => r.data);

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
