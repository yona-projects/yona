/**
 * Yona, 21st Century Project Hosting SW
 *
 * 보안 키(WebAuthn)용 브라우저 도우미이다(추가 개발 항목 X-2, decisions.md D-69).
 * 서버가 주는 요청 JSON(바이너리는 base64url 문자열)을 navigator.credentials의 입력으로 바꾸고,
 * 돌려받은 자격 증명을 서버가 읽는 JSON 문자열로 바꾼다.
 */
(function (global) {
    "use strict";

    function toBuffer(text) {
        var padded = text.replace(/-/g, "+").replace(/_/g, "/");
        while (padded.length % 4) { padded += "="; }
        var binary = atob(padded);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) { bytes[i] = binary.charCodeAt(i); }
        return bytes.buffer;
    }

    function toText(buffer) {
        var bytes = new Uint8Array(buffer);
        var binary = "";
        for (var i = 0; i < bytes.length; i++) { binary += String.fromCharCode(bytes[i]); }
        return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
    }

    function decodeDescriptors(list) {
        return (list || []).map(function (d) { return Object.assign({}, d, { id: toBuffer(d.id) }); });
    }

    function creationOptions(json) {
        var key = JSON.parse(json).publicKey;
        key.challenge = toBuffer(key.challenge);
        key.user.id = toBuffer(key.user.id);
        key.excludeCredentials = decodeDescriptors(key.excludeCredentials);
        return { publicKey: key };
    }

    function requestOptions(json) {
        var key = JSON.parse(json).publicKey;
        key.challenge = toBuffer(key.challenge);
        key.allowCredentials = decodeDescriptors(key.allowCredentials);
        return { publicKey: key };
    }

    function encode(credential) {
        var response = credential.response;
        var out = {
            type: credential.type,
            id: credential.id,
            rawId: toText(credential.rawId),
            authenticatorAttachment: credential.authenticatorAttachment || undefined,
            clientExtensionResults: credential.getClientExtensionResults(),
            response: { clientDataJSON: toText(response.clientDataJSON) }
        };
        if (response.attestationObject) {
            out.response.attestationObject = toText(response.attestationObject);
            out.response.transports = response.getTransports ? response.getTransports() : [];
        } else {
            out.response.authenticatorData = toText(response.authenticatorData);
            out.response.signature = toText(response.signature);
            out.response.userHandle = response.userHandle ? toText(response.userHandle) : null;
        }
        return JSON.stringify(out);
    }

    global.YonaWebAuthn = {
        /** 이 브라우저가 보안 키를 지원하면 true이다. */
        supported: function () { return !!(global.PublicKeyCredential && navigator.credentials); },

        /** 등록 요청 JSON으로 보안 키를 만들고 서버에 보낼 JSON 문자열을 돌려주는 Promise이다. */
        register: function (optionsJson) {
            return navigator.credentials.create(creationOptions(optionsJson)).then(encode);
        },

        /** 인증 요청 JSON으로 보안 키 서명을 받고 서버에 보낼 JSON 문자열을 돌려주는 Promise이다. */
        authenticate: function (requestJson) {
            return navigator.credentials.get(requestOptions(requestJson)).then(encode);
        }
    };
})(window);
