/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * ES 모듈의 진입점이다(ES 모듈 뼈대, D-122). 모든 레이아웃이 `common/scripts.html`에서 이 파일 하나를 `<script type="module">`로 불러온다.
 *
 * 옮겨 온 모듈은 여기서 `import`해 전역 `yona.*`에 등록한다(`ui/Elevator.js`, `ui/Favicon.js`, `ui/FormValidator.js`). 모듈 스크립트는 문서 분석이 끝난 뒤 `DOMContentLoaded` 전에
 * 순서대로 실행되므로, 화면 초기화(`$(function () { ... })`)는 등록이 끝난 뒤에 돈다. 문서 분석 중에 바로 실행되는 인라인 스크립트가 옮긴 모듈을 부르면
 * 아직 없을 수 있으니, 그런 호출은 `yona:esm-ready` 이벤트나 `yona.esm.ready`를 기다려야 한다.
 */
import { register, ensureNamespace } from "yona/namespace.js";

const yona = ensureNamespace("yona");
yona.esm = { ready: false, register, ensureNamespace };

// 옮겨 온 모듈은 아래에 이어서 import한다. 각 모듈은 자기 이름(`yona.ui.X`)으로 옛 전역에도 등록한다.
import "./ui/Elevator.js";
import "./ui/Favicon.js";
import "./ui/FormValidator.js";
import "./http.js";
import "./util.js";
import "./service/temporarySaveHandler.js";
// 번들 yona-lib.js와 yona-common.js에 있던 순서 그대로 불러온다
import "./Request.js";
import "./Attachments.js";
import "./Files.js";
import "./Mention.js";
import "./Markdown.js";
import "./Pagination.js";
import "./ShortcutKey.js";
import "./ui/Dropdown.js";
import "./ui/Typeahead.js";
import "./ui/Dialog.js";
import "./ui/Toast.js";
import "./ui/Tabs.js";
import "./OriginalMessage.js";

yona.esm.ready = true;
document.dispatchEvent(new CustomEvent("yona:esm-ready"));
