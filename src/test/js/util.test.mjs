// esm/util.js의 debounce: 연달아 불러도 마지막 호출만 [wait] 뒤에 한 번 실행한다(직접 만든 clearTimeout과 setTimeout 짝과 같다). 표준 기능 리팩터링 5단계(D-123).
import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const run = async (fn) => { const page = await pageWith({ stubs: false }); const r = await page.evaluate(fn); assert.deepEqual(page.errors, []); await page.close(); return r; };

export const tests = [
  { name: 'debounce: 연달아 부르면 마지막 호출만 지연 뒤에 한 번 실행된다', run: async () => {
    const r = await run(async () => { const calls = []; const d = yona.util.debounce((v) => calls.push(v), 100); d(1); d(2); d(3); const before = calls.slice(); await new Promise((x) => setTimeout(x, 250)); return { before, after: calls }; });
    assert.deepEqual(r, { before: [], after: [3] });
  } },
  { name: 'debounce: 지연 시간이 지나면 다시 부를 수 있고 호출마다 새로 센다', run: async () => {
    const r = await run(async () => { const calls = []; const d = yona.util.debounce((v) => calls.push(v), 80); d('a'); await new Promise((x) => setTimeout(x, 40)); d('b'); await new Promise((x) => setTimeout(x, 40)); const mid = calls.slice(); await new Promise((x) => setTimeout(x, 120)); d('c'); await new Promise((x) => setTimeout(x, 150)); return { mid, calls }; });
    assert.deepEqual(r, { mid: [], calls: ['b', 'c'] });
  } },
  { name: 'debounce: cancel()은 아직 실행하지 않은 호출을 취소한다', run: async () => {
    const r = await run(async () => { const calls = []; const d = yona.util.debounce(() => calls.push(1), 60); d(); d.cancel(); await new Promise((x) => setTimeout(x, 150)); return calls; });
    assert.deepEqual(r, []);
  } },
  { name: 'debounce: 실행할 때의 this와 인자는 마지막 호출의 것이다', run: async () => {
    const r = await run(async () => { const seen = []; const obj = { n: 'o', d: yona.util.debounce(function (a, b) { seen.push([this.n, a, b]); }, 50) }; obj.d(1, 2); obj.d(3, 4); await new Promise((x) => setTimeout(x, 150)); return seen; });
    assert.deepEqual(r, [['o', 3, 4]]);
  } },
  { name: 'debounce: 서로 다른 함수의 지연은 서로 영향을 주지 않는다(비밀번호 칸 두 개)', run: async () => {
    const r = await run(async () => { const calls = []; const a = yona.util.debounce(() => calls.push('a'), 60); const b = yona.util.debounce(() => calls.push('b'), 60); a(); b(); a(); await new Promise((x) => setTimeout(x, 150)); return calls.sort(); });
    assert.deepEqual(r, ['a', 'b']);
  } },
  { name: 'rafThrottle: 한 프레임 안의 여러 호출은 프레임이 끝날 때 마지막 인자로 한 번만 실행된다', run: async () => {
    const r = await run(async () => { const calls = []; const f = yona.util.rafThrottle((v) => calls.push(v)); f(1); f(2); f(3); const before = calls.slice(); await new Promise((x) => requestAnimationFrame(() => requestAnimationFrame(x))); f(4); await new Promise((x) => requestAnimationFrame(() => requestAnimationFrame(x))); return { before, calls }; });
    assert.deepEqual(r, { before: [], calls: [3, 4] });
  } },
  { name: 'rafThrottle: cancel()은 아직 실행하지 않은 호출을 취소한다', run: async () => {
    const r = await run(async () => { const calls = []; const f = yona.util.rafThrottle(() => calls.push(1)); f(); f.cancel(); await new Promise((x) => requestAnimationFrame(() => requestAnimationFrame(x))); return calls; });
    assert.deepEqual(r, []);
  } },
];
