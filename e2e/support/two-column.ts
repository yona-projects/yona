import { Page } from '@playwright/test';

/**
 * 2단 보기 어댑터(yona.turbo.TwoColumn.js)가 초기화를 마칠 때까지 기다린다.
 * 서버가 그린 목록/상세는 스크립트 초기화 전에도 보이지만 이때는 클릭 핸들러와 링크 갱신이 없어서,
 * 화면에 보인다고 바로 조작하면 문서 이동으로 처리되거나 아무 일도 일어나지 않는다.
 * 페이지 이동(goto/reload/폼 제출/댓글 등록 후 새로고침)마다 새 문서가 되므로 이동 뒤에 호출한다.
 */
export async function twoColumnReady(page: Page): Promise<void> {
  await page.locator('[class*="-columns"][data-ready="true"]').first().waitFor({ state: 'attached' });
}
