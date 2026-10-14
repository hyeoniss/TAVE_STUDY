import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const resultPath = __ENV.RESULT_PATH || 'k6-results/result.json';
const targetRate = Number(__ENV.TARGET_RPS || 100);
const duration = __ENV.DURATION || '30s';
const listDuration = new Trend('todo_list_duration', true);
const listRequests = new Counter('todo_list_requests');
const listFailures = new Rate('todo_list_failures');

export const options = {
  scenarios: {
    listTodos: {
      executor: 'constant-arrival-rate',
      rate: targetRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    todo_list_failures: ['rate<0.01'],
    todo_list_duration: ['p(95)<500'],
  },
};

export function setup() {
  const health = http.get(`${baseUrl}/actuator/health`);
  check(health, { 'server is healthy': (response) => response.status === 200 });

  const firstPage = http.get(`${baseUrl}/api/todos?page=0&size=100`);
  check(firstPage, { 'list setup succeeds': (response) => response.status === 200 });

  let totalElements = 0;
  try {
    totalElements = firstPage.json('data.totalElements') || 0;
  } catch (_) {
    totalElements = 0;
  }

  for (let index = totalElements; index < 200; index += 1) {
    const response = http.post(
      `${baseUrl}/api/todos`,
      JSON.stringify({
        title: `k6 performance todo ${index + 1}`,
        memo: 'cache comparison fixture',
        todoDate: '2026-10-12',
      }),
      { headers: { 'Content-Type': 'application/json' } },
    );
    check(response, { 'fixture creation succeeds': (result) => result.status === 201 });
  }

  // 캐시 사용 서버는 측정 전에 한 번 조회해 warm cache 상태로 맞춘다.
  http.get(`${baseUrl}/api/todos?page=0&size=100`);
}

export default function () {
  const response = http.get(`${baseUrl}/api/todos?page=0&size=100`, {
    tags: { endpoint: 'GET /api/todos' },
  });

  listDuration.add(response.timings.duration);
  listRequests.add(1);
  listFailures.add(response.status !== 200);

  check(response, {
    'status is 200': (result) => result.status === 200,
    'response is successful': (result) => result.status === 200 && result.json('success') === true,
    'response contains todos': (result) =>
      result.status === 200 && result.json('data.content').length > 0,
  });
}

export function handleSummary(data) {
  return {
    [resultPath]: JSON.stringify(data, null, 2),
    stdout: textSummary(data),
  };
}

function textSummary(data) {
  const durationMetric = data.metrics.todo_list_duration;
  const requestMetric = data.metrics.todo_list_requests;
  const failureMetric = data.metrics.todo_list_failures;
  const values = durationMetric.values;

  return [
    '',
    `requests: ${requestMetric.values.count}`,
    `throughput: ${requestMetric.values.rate.toFixed(2)} req/s`,
    `avg: ${values.avg.toFixed(2)} ms`,
    `p95: ${values['p(95)'].toFixed(2)} ms`,
    `max: ${values.max.toFixed(2)} ms`,
    `failure rate: ${(failureMetric.values.rate * 100).toFixed(2)}%`,
    '',
  ].join('\n');
}
