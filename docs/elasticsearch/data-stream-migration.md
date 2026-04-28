# 기존 인덱스 → Data Stream 마이그레이션 가이드

> Kibana **Dev Tools > Console** 에서 순서대로 실행한다.
> 4개 인덱스(`log-api`, `log-db`, `log-external`, `log-error`) 각각에 대해 반복한다.
> 아래 예시는 `log-api` 기준이며, 나머지 인덱스는 이름만 변경해서 사용한다.

---

## 사전 조건 확인

```
# ILM 정책 등록 확인
GET /_ilm/policy/log-ilm-policy

# 인덱스 템플릿 등록 확인
GET /_index_template/log-index-template

# 현재 인덱스 상태 확인
GET /log-api
GET /log-db
GET /log-external
GET /log-error
```

---

## STEP 1 — 기존 인덱스 데이터 백업

기존 인덱스를 임시 백업 인덱스에 복사한다.
백업 인덱스는 일반 인덱스이므로 `data_stream` 템플릿 대상(`log-*`)과 이름이 겹치지 않게 `bk-` 접두사를 사용한다.

```
POST /_reindex
{
  "source": { "index": "log-api" },
  "dest":   { "index": "bk-log-api" }
}

POST /_reindex
{
  "source": { "index": "log-db" },
  "dest":   { "index": "bk-log-db" }
}

POST /_reindex
{
  "source": { "index": "log-external" },
  "dest":   { "index": "bk-log-external" }
}

POST /_reindex
{
  "source": { "index": "log-error" },
  "dest":   { "index": "bk-log-error" }
}
```

---

## STEP 2 — 원본 인덱스 삭제

Data Stream은 동일 이름의 일반 인덱스가 존재하면 생성할 수 없다.

```
DELETE /log-api
DELETE /log-db
DELETE /log-external
DELETE /log-error
```

---

## STEP 3 — Data Stream 생성

`log-index-template`이 `log-*` 패턴을 커버하므로 아래 명령 하나로 Data Stream이 생성된다.
(또는 애플리케이션이 첫 문서를 인덱싱할 때 자동 생성된다.)

```
PUT /_data_stream/log-api
PUT /_data_stream/log-db
PUT /_data_stream/log-external
PUT /_data_stream/log-error
```

---

## STEP 4 — 백업 데이터를 Data Stream으로 재인덱싱

Data Stream은 `@timestamp` 필드가 필수이므로, 기존 `timestamp` 값을 `@timestamp`로 복사하는
Painless 스크립트를 함께 실행한다.
`op_type: create` 를 지정해 같은 `_id`의 중복 문서를 방지한다.

```
POST /_reindex
{
  "source": {
    "index": "bk-log-api"
  },
  "dest": {
    "index": "log-api",
    "op_type": "create"
  },
  "script": {
    "lang": "painless",
    "source": "ctx._source['@timestamp'] = ctx._source['timestamp']"
  }
}

POST /_reindex
{
  "source": {
    "index": "bk-log-db"
  },
  "dest": {
    "index": "log-db",
    "op_type": "create"
  },
  "script": {
    "lang": "painless",
    "source": "ctx._source['@timestamp'] = ctx._source['timestamp']"
  }
}

POST /_reindex
{
  "source": {
    "index": "bk-log-external"
  },
  "dest": {
    "index": "log-external",
    "op_type": "create"
  },
  "script": {
    "lang": "painless",
    "source": "ctx._source['@timestamp'] = ctx._source['timestamp']"
  }
}

POST /_reindex
{
  "source": {
    "index": "bk-log-error"
  },
  "dest": {
    "index": "log-error",
    "op_type": "create"
  },
  "script": {
    "lang": "painless",
    "source": "ctx._source['@timestamp'] = ctx._source['timestamp']"
  }
}
```

---

## STEP 5 — 마이그레이션 결과 검증

원본 문서 수와 Data Stream 문서 수가 일치하는지 확인한다.

```
# Data Stream 목록 확인
GET /_data_stream/log-*

# 문서 수 비교 (백업 vs Data Stream)
GET /bk-log-api/_count
GET /log-api/_count

GET /bk-log-db/_count
GET /log-db/_count

GET /bk-log-external/_count
GET /log-external/_count

GET /bk-log-error/_count
GET /log-error/_count

# ILM 정책 연결 확인
GET /log-api/_ilm/explain
```

---

## STEP 6 — 백업 인덱스 삭제

문서 수가 일치함을 확인한 후 백업 인덱스를 삭제한다.

```
DELETE /bk-log-api
DELETE /bk-log-db
DELETE /bk-log-external
DELETE /bk-log-error
```

---

## 참고: Data Stream 구조 확인

```
# backing index 이름 확인 (.ds-log-api-YYYY.MM.dd-000001 형태)
GET /_data_stream/log-api

# 특정 backing index의 ILM 단계 확인
GET /.ds-log-api-*/_ilm/explain
```

---

## 주의 사항

| 항목 | 설명 |
|------|------|
| `@timestamp` 필수 | Data Stream은 `@timestamp` 필드가 없으면 문서 인덱싱을 거부한다. `ElasticsearchLogConsumer`에서 문서 생성 시 `@timestamp` 를 함께 설정해야 한다. |
| `_id` 제한 없음 | ES 8.x Data Stream은 `op_type: create` 에서 커스텀 `_id` 허용. 재처리 멱등성은 유지된다. |
| 롤오버 기준 | `max_age: 7d` 또는 `max_primary_shard_size: 50gb` 초과 시 새 backing index 자동 생성. |
| `bk-` 접두사 | 백업 인덱스가 `log-*` 패턴에 걸리지 않도록 이름을 `bk-`로 시작해야 한다. |
