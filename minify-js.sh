#################
# yona-layout.js
#################

CLOSURE_COMPILER="public/compiler.jar"
OUT_DIR="public/javascripts"
PUBLIC="public"
PUBLIC_JAVASCRIPTS="public/javascripts"

java -jar $CLOSURE_COMPILER --js $PUBLIC_JAVASCRIPTS/lib/nprogress/nprogress.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery-3.3.1.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.browser.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.pjax.js $PUBLIC_JAVASCRIPTS/common/yobi.Common.js --js_output_file $OUT_DIR/yona-layout.js

# for dev 
#cat $PUBLIC_JAVASCRIPTS/lib/nprogress/nprogress.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery-3.3.1.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.browser.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.pjax.js $PUBLIC_JAVASCRIPTS/common/yobi.Common.js > $OUT_DIR/yona-layout.js

#################
# yona-common.js
#################

java -jar $CLOSURE_COMPILER --js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.tmpl.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.form.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.validate.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.requestAs.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.search.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.zclip.min.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.placeholder.min.js $PUBLIC/bootstrap/js/bootstrap.js $PUBLIC_JAVASCRIPTS/lib/rgbcolor.js $PUBLIC_JAVASCRIPTS/lib/humanize.js $PUBLIC_JAVASCRIPTS/lib/validate.js $PUBLIC_JAVASCRIPTS/lib/xss.js $PUBLIC_JAVASCRIPTS/lib/clipboard.js --js_output_file $OUT_DIR/yona-common.js

# for dev
# cat $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.tmpl.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.form.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.validate.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.requestAs.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.search.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.zclip.min.js $PUBLIC_JAVASCRIPTS/lib/jquery/jquery.placeholder.min.js $PUBLIC/bootstrap/js/bootstrap.js $PUBLIC_JAVASCRIPTS/lib/rgbcolor.js $PUBLIC_JAVASCRIPTS/lib/humanize.js $PUBLIC_JAVASCRIPTS/lib/validate.js $PUBLIC_JAVASCRIPTS/lib/xss.js $PUBLIC_JAVASCRIPTS/lib/clipboard.js > $OUT_DIR/yona-common.js

# yona-lib.js
java -jar $CLOSURE_COMPILER --js $PUBLIC_JAVASCRIPTS/common/yobi.Attachments.js $PUBLIC_JAVASCRIPTS/common/yobi.Files.js $PUBLIC_JAVASCRIPTS/common/yobi.Mention.js $PUBLIC_JAVASCRIPTS/common/yobi.Markdown.js $PUBLIC_JAVASCRIPTS/common/yobi.Pagination.js $PUBLIC_JAVASCRIPTS/common/yobi.ShortcutKey.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Dropdown.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Typeahead.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Dialog.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Toast.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Tabs.js $PUBLIC_JAVASCRIPTS/common/yobi.OriginalMessage.js $PUBLIC_JAVASCRIPTS/service/yona.temporarySaveHandler.js --js_output_file $OUT_DIR/yona-lib.js

# for dev
# cat $PUBLIC_JAVASCRIPTS/common/yobi.Attachments.js $PUBLIC_JAVASCRIPTS/common/yobi.Files.js $PUBLIC_JAVASCRIPTS/common/yobi.Mention.js $PUBLIC_JAVASCRIPTS/common/yobi.Markdown.js $PUBLIC_JAVASCRIPTS/common/yobi.Pagination.js $PUBLIC_JAVASCRIPTS/common/yobi.ShortcutKey.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Dropdown.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Typeahead.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Dialog.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Toast.js $PUBLIC_JAVASCRIPTS/common/yobi.ui.Tabs.js $PUBLIC_JAVASCRIPTS/common/yobi.OriginalMessage.js $PUBLIC_JAVASCRIPTS/service/yona.temporarySaveHandler.js > $OUT_DIR/yona-lib.js
