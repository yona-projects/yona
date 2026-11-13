import { register } from "../namespace.js";

/**
 * 변경된 내용을 보여 주는 화면(커밋, 비교, 코드 주고받기 변경 내용, Subversion 커밋과 비교)에 변경된 파일의 경로 트리를 더한다.
 *
 * v1.6에는 없는 추가 기능이다(decisions.md A-571). 이 모듈은 서버가 그린 diff 블록(`.diff-partial-outer`)을 그대로 두고 화면에서만 다룬다.
 * 변경 파일이 둘 이상일 때 diff 왼쪽에 경로 트리를 두고, 파일을 고르면 그 파일의 diff만, 폴더를 고르면 그 폴더 아래(하위 폴더 포함) 변경된
 * 파일의 diff를 모두 보인다. 같은 항목을 다시 누르거나 "전체 보기"를 누르면 모든 파일이 보인다. 파일이 하나뿐이면 트리가 필요 없어
 * 아무것도 하지 않는다.
 *
 * Subversion 화면은 diff를 스크립트가 나중에 그리므로, diff 블록이 나타날 때까지 짧게 기다렸다가 트리를 만든다.
 *
 * @param {Object} htOptions
 * @param {string} [htOptions.sContainer=".diff-body"] diff 블록을 담은 요소의 선택자
 */
const DiffTree = function(htOptions){

    const BLOCK = ".diff-partial-outer";
    const RETRY_LIMIT = 20;
    const RETRY_DELAY = 150;

    let welContainer = null;
    let aBlocks = [];

    /**
     * 모듈을 시작한다. 이미 트리를 만든 화면이면 중복해서 만들지 않는다.
     * @param {Object} htOptions 옵션
     */
    function _init(htOptions){
        welContainer = $((htOptions && htOptions.sContainer) || ".diff-body").first();
        if (welContainer.length === 0 || welContainer.data("diffTree")) {
            return;
        }
        _waitForBlocks(0);
    }

    /**
     * diff 블록이 그려질 때까지 기다린다. 그려지지 않은 채 한도에 이르면 포기한다(변경이 없는 화면).
     * @param {number} nTry 지금까지 확인한 횟수
     */
    function _waitForBlocks(nTry){
        if (welContainer.find(BLOCK).length > 0) {
            _build();
        } else if (nTry < RETRY_LIMIT) {
            setTimeout(function(){ _waitForBlocks(nTry + 1); }, RETRY_DELAY);
        }
    }

    /**
     * 블록의 파일 경로이다. 서버가 표에 적어 둔 경로를 먼저 쓰고, 없으면 머리글의 파일 이름을 쓴다.
     * @param {jQuery} welBlock diff 블록
     * @return {string} 파일 경로
     */
    function _pathOf(welBlock){
        const sPath = welBlock.find("table.diff-container").first().attr("data-file-path");
        return (sPath && sPath.length > 0) ? sPath : $.trim(welBlock.find(".filename").first().text());
    }

    /**
     * 트리를 만들어 diff 왼쪽에 끼운다.
     */
    function _build(){
        const welBlocks = welContainer.find(BLOCK);
        if (welBlocks.length < 2 || welContainer.data("diffTree")) {
            return;
        }
        welContainer.data("diffTree", true);
        aBlocks = welBlocks.toArray();

        const root = {folders: {}, order: [], files: [], indices: []};
        aBlocks.forEach(function(elBlock, nIndex){
            const aParts = _pathOf($(elBlock)).split("/").filter(function(s){ return s.length > 0; });
            let node = root;
            aParts.slice(0, -1).forEach(function(sDir){
                if (!node.folders[sDir]) {
                    node.folders[sDir] = {folders: {}, order: [], files: [], indices: []};
                    node.order.push(sDir);
                }
                node = node.folders[sDir];
                node.indices.push(nIndex);
            });
            node.files.push({name: aParts.length > 0 ? aParts[aParts.length - 1] : "?", index: nIndex});
        });

        const welAside = $('<aside class="diff-tree"></aside>');
        const welHead = $('<div class="diff-tree-head"></div>');
        welHead.append($('<strong class="diff-tree-title"></strong>').text(Messages("code.diff.tree.title", aBlocks.length)));
        welHead.append($('<button type="button" class="diff-tree-all"></button>').text(Messages("code.diff.tree.all")));
        welAside.append(welHead).append(_renderNode(root));

        const welLayout = $('<div class="diff-tree-layout"></div>');
        welContainer.before(welLayout);
        welLayout.append(welAside).append(welContainer);

        // 파일 줄 전체를 누를 수 있어야 하므로 링크가 아니라 줄에 이벤트를 건다
        welAside.on("click", "li.file", function(e){
            e.preventDefault();
            e.stopPropagation();
            _select($(this), [parseInt($(this).attr("data-index"), 10)]);
        });
        // 폴더 이름을 누르면 그 폴더 아래 변경된 파일을 모두 보인다. 폴더 줄 안의 하위 목록을 누른 것은 건드리지 않는다
        welAside.on("click", "li.folder > .name", function(e){
            e.stopPropagation();
            const welFolder = $(this).parent();
            _select(welFolder, String(welFolder.attr("data-indices")).split(",").map(function(s){ return parseInt(s, 10); }));
        });
        welAside.on("click", ".diff-tree-all", function(){
            _select(null, []);
        });
    }

    /**
     * 폴더 한 단계를 목록으로 그린다. 폴더를 먼저, 파일을 그 뒤에 둔다.
     * @param {Object} node 폴더 노드
     * @return {jQuery} 목록 요소
     */
    function _renderNode(node){
        const welList = $('<ul class="diff-tree-list"></ul>');
        node.order.forEach(function(sDir){
            const welFolder = $('<li class="folder"></li>').attr("data-indices", node.folders[sDir].indices.join(","));
            welFolder.append($('<span class="name"></span>').text(sDir));
            welFolder.append(_renderNode(node.folders[sDir]));
            welList.append(welFolder);
        });
        node.files.forEach(function(file){
            const welFile = $('<li class="file"></li>').attr("data-index", file.index);
            welFile.append($('<a href="#"></a>').text(file.name));
            welList.append(welFile);
        });
        return welList;
    }

    /**
     * 트리 항목(파일 또는 폴더)을 골라 그 항목에 속한 diff만 보이게 한다. 이미 고른 항목을 다시 고르거나 항목이 없으면 전체를 보인다.
     * @param {jQuery|null} welItem 고른 트리 항목, 전체 보기는 null
     * @param {number[]} aIndices 보일 diff 블록의 번호들
     */
    function _select(welItem, aIndices){
        const welTree = welContainer.parent().find(".diff-tree");
        const bToggleOff = welItem !== null && welItem.hasClass("active");
        const aShown = (bToggleOff || welItem === null) ? [] : aIndices;
        const bAll = aShown.length === 0;

        aBlocks.forEach(function(elBlock, i){
            const bHide = !bAll && aShown.indexOf(i) < 0;
            $(elBlock).toggleClass("diff-tree-hidden", bHide).css("display", bHide ? "none" : "");
        });
        welTree.find("li.active").removeClass("active");
        if (!bAll) {
            welItem.addClass("active");
        }
        welTree.find(".diff-tree-all").toggleClass("active", bAll);
    }

    _init(htOptions);
};

export default DiffTree;

register("yona.code.DiffTree", DiffTree);
