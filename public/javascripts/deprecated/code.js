nforge.namespace('code');

nforge.code.copy = function() {
    return {
        init: function() {
            $('#copy-url').zclip({
                path: '/assets/javascripts/ZeroClipboard.swf',
                copy: function() {
                    return $("#repo-url").attr('value');
                }
            });
        }
    }
}
