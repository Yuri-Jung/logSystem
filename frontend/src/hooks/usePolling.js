import { useEffect, useRef, useCallback } from 'react';

/**
 * 주기적으로 콜백을 실행하는 폴링 훅.
 *
 * 전략 선택 근거:
 * - 현재 구조(REST API)에서 WebSocket은 서버 측 추가 설정이 크다.
 * - 로그 대시보드는 "준실시간(near real-time)" 수준이면 충분하다.
 * - 30초 폴링은 ES 클러스터 부하와 UI 갱신 빈도 사이의 실용적인 균형점이다.
 *
 * 업그레이드 경로:
 * 1. SSE(Server-Sent Events): Spring SseEmitter + EventSource — 서버 push, 단방향
 * 2. WebSocket: Spring WebSocket + STOMP — 양방향, Kafka consumer와 직결 가능
 *
 * @param {() => void} callback  폴링마다 실행할 함수
 * @param {number}     interval  폴링 간격(ms), 기본 30초
 * @param {boolean}    immediate 마운트 즉시 첫 호출 여부, 기본 true
 */
export function usePolling(callback, interval = 30_000, immediate = true) {
  const savedCallback = useRef(callback);
  const timerRef      = useRef(null);

  // callback이 바뀌어도 클로저가 낡아지지 않도록 ref에 최신 값 유지
  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  const stop = useCallback(() => {
    clearInterval(timerRef.current);
  }, []);

  useEffect(() => {
    if (immediate) savedCallback.current();
    timerRef.current = setInterval(() => savedCallback.current(), interval);
    return stop;
  }, [interval, immediate, stop]);

  return { stop };
}
