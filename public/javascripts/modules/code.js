nforge.namespace('code');
nforge.code.branch = function () {
    return {
        init: function() {
            $('#branch').click(this.update);
        },

        update: function() {
            window.location.replace($('#branch').val());
        }
    };
};
