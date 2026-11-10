/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * `fetch`로 보내는 요청 도우미이다(표준 기능 리팩터링 3단계, D-123). 화면 스크립트가 쓰던 `$.ajax`, `$.get`, `$.post`, `$.getJSON`과 같은 호출 모양과 결과를 주므로
 * 호출 한 줄만 바꿔 옮길 수 있고(`$.ajax(` → `yona.http.ajax(`), 옮긴 뒤에는 `await`로 쓸 수 있다.
 *
 * 같게 맞춘 것
 * - 주소, 방식(`method`나 `type`), 보낼 값(`data`): 객체는 `jQuery.param`과 같은 규칙으로 `key=value&...`(배열은 `key[]`, 객체는 `key[sub]`, 공백은 `%20`)로 만든다.
 *   GET과 HEAD는 주소 뒤에 붙이고 그 밖에는 본문으로 보낸다(`contentType` 기본 `application/x-www-form-urlencoded; charset=UTF-8`, `false`면 머리글을 달지 않는다, `processData: false`면 그대로 보낸다).
 * - 머리글: `Accept`(`dataType`에 따른 값), 같은 출처에는 `X-Requested-With: XMLHttpRequest`, `headers`, `beforeSend(xhr)`의 `xhr.setRequestHeader`.
 *   CSRF 토큰은 `yona.Csrf.js`가 `fetch`에 싣는다.
 * - 결과: 2xx와 304는 성공이다. `dataType`이 `json`이면 JSON으로 읽고(비었거나 깨졌으면 `parsererror`, 204는 성공), 없으면 응답 `Content-Type`이 json일 때만 읽는다.
 *   `success(data, textStatus, xhr)`, `error(xhr, textStatus, errorThrown)`, `complete(xhr, textStatus)`를 `context`를 `this`로 불러 주고, 돌려주는 객체의
 *   `done`, `fail`, `always`, `then`, `catch`가 같은 인자를 받는다. `xhr`는 `status`, `statusText`, `responseText`, `responseJSON`, `getResponseHeader`를 가진다.
 *
 * 알려진 차이: 취소(`xhr.abort()`)와 `async: false`, `jsonp`, `xhrFields`는 지원하지 않는다(쓰는 곳이 없다).
 */
import { register } from "./namespace.js";

/** `jQuery.param`과 같은 규칙으로 값을 `key=value&...` 글자로 만든다. [traditional]이 참이면 배열을 `key[]`가 아니라 `key=1&key=2`로 쓴다. */
export function param(data, traditional = false) {
  const parts = [];
  const add = (key, value) => {
    const v = typeof value === "function" ? value() : (value == null ? "" : value);
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(v)}`);
  };
  const build = (prefix, value) => {
    if (Array.isArray(value)) {
      value.forEach((item, index) => {
        if (traditional || /\[\]$/.test(prefix)) {
          add(prefix, item);
        } else {
          build(`${prefix}[${typeof item === "object" && item != null ? index : ""}]`, item);
        }
      });
    } else if (value != null && typeof value === "object") {
      Object.keys(value).forEach((name) => build(`${prefix}[${name}]`, value[name]));
    } else {
      add(prefix, value);
    }
  };
  if (Array.isArray(data)) {
    data.forEach((field) => add(field.name, field.value));
  } else if (traditional) {
    // 전통 방식: 한 단계만 풀고 배열은 같은 이름을 되풀이한다(중첩 객체는 글자로 바뀐다)
    Object.keys(data || {}).forEach((key) => [].concat(data[key]).forEach((item) => add(key, item)));
  } else {
    Object.keys(data || {}).forEach((key) => build(key, data[key]));
  }
  return parts.join("&");
}

const ACCEPTS = {
  json: "application/json, text/javascript, */*; q=0.01",
  text: "text/plain, */*; q=0.01",
  html: "text/html, */*; q=0.01",
  xml: "application/xml, text/xml, */*; q=0.01",
  script: "text/javascript, application/javascript, application/ecmascript, application/x-ecmascript, */*; q=0.01",
};

function sameOrigin(url) {
  try {
    return new URL(url, window.location.href).origin === window.location.origin;
  } catch (e) {
    return true;
  }
}

/** `$.ajax`가 돌려주던 jqXHR 모양의 응답 객체를 만든다. */
function createXhr(response, text) {
  const xhr = {
    readyState: 4,
    status: response ? response.status : 0,
    statusText: response ? response.statusText : "error",
    responseText: text || "",
    getResponseHeader: (name) => (response ? response.headers.get(name) : null),
    getAllResponseHeaders: () => (response ? [...response.headers].map(([k, v]) => `${k}: ${v}`).join("\r\n") : ""),
    setRequestHeader: () => xhr,
  };
  return xhr;
}

/**
 * 요청을 보낸다.
 *
 * @param {string|object} urlOrOptions 주소, 또는 `url`을 가진 옵션
 * @param {object} [maybeOptions] 옵션(`method`, `type`, `data`, `dataType`, `contentType`, `processData`, `headers`, `cache`, `beforeSend`, `success`, `error`, `complete`, `context`)
 * @returns {object} `done`, `fail`, `always`, `then`, `catch`와 `readyState`, `status`, `responseText` 등을 가진 jqXHR 모양의 객체
 */
export function ajax(urlOrOptions, maybeOptions) {
  const options = typeof urlOrOptions === "object" ? { ...urlOrOptions } : { ...maybeOptions, url: urlOrOptions };
  let url = options.url || window.location.href;
  const method = String(options.method || options.type || "GET").toUpperCase();
  const dataType = options.dataType;
  const context = options.context || options;
  const headers = {};
  const hasBody = !/^(GET|HEAD)$/.test(method);
  let body;

  headers.Accept = ACCEPTS[dataType] || "*/*";

  if (options.data !== undefined && options.data !== null) {
    const data = typeof options.data === "string" || options.processData === false ? options.data : param(options.data, options.traditional);
    if (hasBody) {
      body = data;
      if (options.contentType !== false) {
        headers["Content-Type"] = options.contentType || "application/x-www-form-urlencoded; charset=UTF-8";
      }
      // 본문이 urlencoded이면 공백은 +로 쓴다(주소에 붙는 GET의 값은 %20 그대로)
      if (options.processData !== false && typeof data === "string" && /^application\/x-www-form-urlencoded/.test(headers["Content-Type"] || "")) {
        body = data.replace(/%20/g, "+");
      }
    } else if (data !== "") {
      url += (url.includes("?") ? "&" : "?") + data;
    }
  } else if (hasBody && options.contentType && options.contentType !== false) {
    headers["Content-Type"] = options.contentType;
  }
  if (options.cache === false && !hasBody) {
    url += `${url.includes("?") ? "&" : "?"}_=${Date.now()}`;
  }
  if (sameOrigin(url)) {
    headers["X-Requested-With"] = "XMLHttpRequest";
  }
  Object.assign(headers, options.headers);

  const pending = createXhr(null, "");
  pending.readyState = 1;
  pending.setRequestHeader = (name, value) => { headers[name] = value; return pending; };
  if (typeof options.beforeSend === "function" && options.beforeSend.call(context, pending, options) === false) {
    const aborted = createXhr(null, "");
    aborted.statusText = "canceled";
    return settle(Promise.reject({ xhr: aborted, textStatus: "canceled", error: "canceled" }), aborted, options, context);
  }

  const run = fetch(url, { method, headers, body, credentials: "same-origin" }).then(
    (response) => response.text().then((text) => {
      const xhr = createXhr(response, text);
      const ok = (response.status >= 200 && response.status < 300) || response.status === 304;
      if (!ok) {
        // jQuery는 오류 응답도 dataType(없으면 응답 Content-Type)에 따라 읽어 responseJSON을 채운다. 읽지 못하면 비워 둔다
        if (dataType === "json" || (!dataType && /json/i.test(response.headers.get("Content-Type") || ""))) {
          try { xhr.responseJSON = JSON.parse(text); } catch (e) { /* 본문이 JSON이 아니면 responseJSON은 없다 */ }
        }
        throw { xhr, textStatus: "error", error: response.statusText };
      }
      let data = text;
      let textStatus = "success";
      if (response.status === 204 || method === "HEAD") {
        data = undefined;
        textStatus = "nocontent";
      } else if (response.status === 304) {
        data = undefined;
        textStatus = "notmodified";
      } else if (dataType === "json" || (!dataType && /json/i.test(response.headers.get("Content-Type") || ""))) {
        try {
          data = JSON.parse(text);
          xhr.responseJSON = data;
        } catch (e) {
          throw { xhr, textStatus: "parsererror", error: e };
        }
      }
      return { data, textStatus, xhr };
    }),
    (e) => { throw { xhr: createXhr(null, ""), textStatus: "error", error: "" }; }
  );
  return settle(run, null, options, context);
}

/** 결과 약속에 jQuery 모양의 `done/fail/always/then/catch`를 붙이고 `success/error/complete` 옵션을 부른다. */
function settle(promise, fallbackXhr, options, context) {
  let lastXhr = fallbackXhr;
  const outcome = promise.then(
    (r) => { lastXhr = r.xhr; return { ok: true, ...r }; },
    (e) => { lastXhr = e.xhr; return { ok: false, ...e }; }
  );
  const done = outcome.then((r) => {
    if (r.ok) {
      if (typeof options.success === "function") options.success.call(context, r.data, r.textStatus, r.xhr);
    } else if (typeof options.error === "function") {
      options.error.call(context, r.xhr, r.textStatus, r.error);
    }
    if (typeof options.complete === "function") options.complete.call(context, r.xhr, r.textStatus);
    return r;
  });
  const jq = Object.create(null);
  const call = (handler, args) => handler.apply(context, args);
  jq.done = (fn) => { done.then((r) => { if (r.ok) call(fn, [r.data, r.textStatus, r.xhr]); }); return jq; };
  jq.fail = (fn) => { done.then((r) => { if (!r.ok) call(fn, [r.xhr, r.textStatus, r.error]); }); return jq; };
  jq.always = (fn) => { done.then((r) => call(fn, r.ok ? [r.data, r.textStatus, r.xhr] : [r.xhr, r.textStatus, r.error])); return jq; };
  jq.then = (onOk, onFail) => done.then((r) => (r.ok ? (onOk ? call(onOk, [r.data, r.textStatus, r.xhr]) : r) : (onFail ? call(onFail, [r.xhr, r.textStatus, r.error]) : Promise.reject(r))));
  jq.catch = (onFail) => jq.then(undefined, onFail);
  jq.promise = () => jq;
  ["readyState", "status", "statusText", "responseText", "responseJSON"].forEach((key) => Object.defineProperty(jq, key, { get: () => (lastXhr ? lastXhr[key] : (key === "readyState" ? 1 : undefined)) }));
  jq.getResponseHeader = (name) => (lastXhr ? lastXhr.getResponseHeader(name) : null);
  jq.getAllResponseHeaders = () => (lastXhr ? lastXhr.getAllResponseHeaders() : "");
  jq.setRequestHeader = () => jq;
  return jq;
}

/** `$.get(url, data, success, dataType)`와 같은 인자 모양(데이터 생략 가능). */
function shorthand(method) {
  return (url, data, success, dataType) => {
    if (typeof data === "function") {
      dataType = dataType || success;
      success = data;
      data = undefined;
    }
    return ajax({ url, method, data, success, dataType });
  };
}

export const get = shorthand("GET");
export const post = shorthand("POST");
export const getJSON = (url, data, success) => {
  const a = shorthand("GET");
  return a(url, data, success, "json");
};

export const http = { ajax, get, post, getJSON, param };

register("yona.http", http);
