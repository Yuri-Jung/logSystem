/**
 * 단일 지표를 카드 형태로 표시하는 컴포넌트.
 *
 * @param {string}  title   카드 제목
 * @param {string}  value   표시할 값
 * @param {string}  unit    단위 텍스트 (선택)
 * @param {string}  color   값 텍스트 색상 Tailwind 클래스 (기본 text-gray-900)
 * @param {boolean} loading 로딩 상태 여부
 */
export function StatCard({ title, value, unit, color = 'text-gray-900', loading }) {
  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5">
      <p className="text-sm font-medium text-gray-500">{title}</p>
      {loading ? (
        <div className="mt-2 h-8 w-24 bg-gray-100 rounded animate-pulse" />
      ) : (
        <p className={`mt-2 text-3xl font-bold ${color}`}>
          {value}
          {unit && (
            <span className="ml-1 text-base font-normal text-gray-400">{unit}</span>
          )}
        </p>
      )}
    </div>
  );
}
