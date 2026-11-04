import assert from 'node:assert/strict';
import { pageWith } from './harness.mjs';

const HTML = `<form><div class="textarea-box"><textarea></textarea></div><div class="attachment-files"></div><input type="hidden" class="temporaryUploadFiles" value=""><input type="file" class="file-upload__input" multiple></form>`;
const STUBS = { content: `
  window.NProgress = { start: function () {}, done: function () {} };
  window.humanize = { filesize: function (n) { return n + 'B'; } };
  window.__posts = [];
  $.post = function (u) { window.__posts.push(u); var d = $.Deferred(); return d.promise(); };` };

/** 파일 입력에 [count]개를 올리면 서버가 [ids]를 차례로 돌려주는 상황을 만든다. */
async function upload(ids, doDelete) {
  const page = await pageWith({ html: HTML, scripts: [STUBS] });
  await page.evaluate(() => { window.__src = null; });
  await page.addScriptTag({ path: (await import('./harness.mjs')).JS + '/common/yona.CommentAttachmentsUpdate.js' });
  const state = await page.evaluate(async ([list, doDelete]) => {
    var n = 0;
    $.ajax = function () {
      var d = $.Deferred(); var id = list[n++];
      setTimeout(function () { d.resolve({ id: id, name: 'f' + id + '.txt', url: '/f/' + id, mimeType: 'text/plain', size: 1 }); }, 0);
      return d.promise();
    };
    var input = $('.file-upload__input')[0];
    // 같은 입력에서 올리는 일을 두 번 되풀이한다: 첫 번째에는 파일 하나, 두 번째에도 하나
    for (var round = 0; round < list.length; round++) {
      var dt = new DataTransfer(); dt.items.add(new File(['x'], 'a.txt')); input.files = dt.files;
      $(input).trigger('change');
      await new Promise((r) => setTimeout(r, 30));
    }
    if (doDelete) $('.attachment-files .btn-delete').first().trigger('click');
    return { ids: $('.temporaryUploadFiles').val(), posts: window.__posts.slice() };
  }, [ids, doDelete]);
  const errors = page.errors; await page.close();
  return { ...state, errors };
}

export const tests = [
  { name: '같은 파일 아이디가 숫자로 다시 와도 목록에 한 번만 들어간다 (C008)', async run() {
    const r = await upload([5, 5], false);
    assert.deepEqual(r.errors, [], r.errors.join(' / '));
    assert.equal(r.ids, '5', '아이디가 중복되었다: ' + r.ids);
  } },
  { name: '업로드를 거듭해도 삭제 단추는 한 번만 요청을 보낸다 (C007)', async run() {
    const r = await upload([5, 6, 7], true);
    assert.deepEqual(r.errors, [], r.errors.join(' / '));
    assert.equal(r.posts.length, 1, '삭제 요청이 ' + r.posts.length + '번 나갔다');
  } },
];
