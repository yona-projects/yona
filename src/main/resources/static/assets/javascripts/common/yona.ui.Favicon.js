/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

/**
 * 브라우저 탭의 파비콘 위에 작은 배지(예: 새 변경이 있다는 `N`)를 그려 준다. 이슈 보기가 다른 사람이 글을 고쳤는지 감지해 알릴 때 쓴다.
 * v1.6이 쓰던 외부 라이브러리 `Favico.js`(0.3.10, 2016년 이후 유지 중단)에서 이 화면이 쓰던 기능(위치, 배경색, 글자 배지, 되돌리기)만 캔버스로 다시 썼다(D-122 3단계).
 *
 * 사용
 * <pre>
 * var favicon = new yona.ui.Favicon({"position": "up", "bgColor": "#4183c4"});
 * favicon.badge("N");   // 배지를 그린다
 * favicon.reset();      // 원래 아이콘으로 되돌린다
 * </pre>
 *
 * 옵션
 * - `position`: `down`(오른쪽 아래, 기본), `up`(오른쪽 위), `left`(왼쪽 아래), `upleft`(왼쪽 위)
 * - `bgColor`: 배지의 배경색(기본 `#d00`)
 * - `textColor`: 글자색(기본 `#fff`)
 *
 * 배지는 아이콘 한 변의 0.6배인 원이고 글자는 가운데에 굵게 쓴다. 아이콘을 읽어 오지 못하면(주소가 없거나 다른 출처) 아이콘 없이 배지만 그린다.
 */
(function (window, document) {
    "use strict";

    var yona = window.yona = window.yona || {};
    yona.ui = yona.ui || {};

    /**
     * @param {Object} [htOptions] `position`, `bgColor`, `textColor`
     * @constructor
     */
    function Favicon(htOptions) {
        var htOpt = htOptions || {};
        var sPosition = htOpt.position || "down";
        var sBgColor = htOpt.bgColor || "#d00";
        var sTextColor = htOpt.textColor || "#fff";

        var elLink = document.querySelector('link[rel$="icon"]');
        if (!elLink) {
            elLink = document.createElement("link");
            elLink.setAttribute("rel", "icon");
            document.head.appendChild(elLink);
        }
        var sOriginalHref = elLink.getAttribute("href") || "";
        var sOriginalType = elLink.getAttribute("type");
        var elImage = null;

        /** 원래 아이콘을 한 번만 읽는다. 다 읽으면 [fCallback]에 이미지(읽지 못하면 null)를 넘긴다. */
        function loadIcon(fCallback) {
            if (elImage) {
                fCallback(elImage.complete && elImage.naturalWidth > 0 ? elImage : null);
                return;
            }
            elImage = new Image();
            elImage.onload = function () { fCallback(elImage); };
            elImage.onerror = function () { fCallback(null); };
            elImage.src = sOriginalHref;
        }

        /**
         * 배지를 그린다.
         *
         * @param {String|Number} vText 배지에 쓸 글자(예: "N")
         */
        this.badge = function (vText) {
            loadIcon(function (elIcon) {
                var nWidth = (elIcon && elIcon.naturalWidth) || 32;
                var nHeight = (elIcon && elIcon.naturalHeight) || 32;
                var elCanvas = document.createElement("canvas");
                elCanvas.width = nWidth;
                elCanvas.height = nHeight;
                var oContext = elCanvas.getContext("2d");

                if (elIcon) {
                    oContext.drawImage(elIcon, 0, 0, nWidth, nHeight);
                }

                var nSize = Math.floor(Math.min(nWidth, nHeight) * 0.6);
                var nLeft = sPosition.indexOf("left") >= 0 ? 0 : nWidth - nSize;
                var nTop = sPosition.indexOf("up") >= 0 ? 0 : nHeight - nSize;

                oContext.beginPath();
                oContext.arc(nLeft + nSize / 2, nTop + nSize / 2, nSize / 2, 0, Math.PI * 2);
                oContext.closePath();
                oContext.fillStyle = sBgColor;
                oContext.fill();

                oContext.fillStyle = sTextColor;
                oContext.font = "bold " + Math.round(nSize * 0.9) + "px sans-serif";
                oContext.textAlign = "center";
                oContext.textBaseline = "middle";
                oContext.fillText(String(vText), nLeft + nSize / 2, nTop + nSize / 2 + nSize * 0.05);

                elLink.setAttribute("type", "image/png");
                elLink.setAttribute("href", elCanvas.toDataURL("image/png"));
            });
        };

        /** 원래 아이콘으로 되돌린다. */
        this.reset = function () {
            elLink.setAttribute("href", sOriginalHref);
            if (sOriginalType === null) {
                elLink.removeAttribute("type");
            } else {
                elLink.setAttribute("type", sOriginalType);
            }
        };
    }

    yona.ui.Favicon = Favicon;
})(window, document);
