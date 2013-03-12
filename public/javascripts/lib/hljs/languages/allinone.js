
/*
Language: ActionScript
Author: Alexander Myadzel <myadzel@gmail.com>
*/

hljs.LANGUAGES.actionscript = function() {
  var IDENT_RE = '[a-zA-Z_$][a-zA-Z0-9_$]*';
  var IDENT_FUNC_RETURN_TYPE_RE = '([*]|[a-zA-Z_$][a-zA-Z0-9_$]*)';

  var AS3_REST_ARG_MODE = {
    className: 'rest_arg',
    begin: '[.]{3}', end: IDENT_RE,
    relevance: 10
  };
  var TITLE_MODE = {className: 'title', begin: IDENT_RE};

  return {
    defaultMode: {
      keywords: {
        keyword: 'as break case catch class const continue default delete do dynamic each ' +
          'else extends final finally for function get if implements import in include ' +
          'instanceof interface internal is namespace native new override package private ' +
          'protected public return set static super switch this throw try typeof use var void ' +
          'while with',
        literal: 'true false null undefined'
      },
      contains: [
        hljs.APOS_STRING_MODE,
        hljs.QUOTE_STRING_MODE,
        hljs.C_LINE_COMMENT_MODE,
        hljs.C_BLOCK_COMMENT_MODE,
        hljs.C_NUMBER_MODE,
        {
          className: 'package',
          beginWithKeyword: true, end: '{',
          keywords: 'package',
          contains: [TITLE_MODE]
        },
        {
          className: 'class',
          beginWithKeyword: true, end: '{',
          keywords: 'class interface',
          contains: [
            {
              beginWithKeyword: true,
              keywords: 'extends implements'
            },
            TITLE_MODE
          ]
        },
        {
          className: 'preprocessor',
          beginWithKeyword: true, end: ';',
          keywords: 'import include'
        },
        {
          className: 'function',
          beginWithKeyword: true, end: '[{;]',
          keywords: 'function',
          illegal: '\\S',
          contains: [
            TITLE_MODE,
            {
              className: 'params',
              begin: '\\(', end: '\\)',
              contains: [
                hljs.APOS_STRING_MODE,
                hljs.QUOTE_STRING_MODE,
                hljs.C_LINE_COMMENT_MODE,
                hljs.C_BLOCK_COMMENT_MODE,
                AS3_REST_ARG_MODE
              ]
            },
            {
              className: 'type',
              begin: ':',
              end: IDENT_FUNC_RETURN_TYPE_RE,
              relevance: 10
            }
          ]
        }
      ]
    }
  }
}();

/*
Language: Apache
Author: Ruslan Keba <rukeba@gmail.com>
Website: http://rukeba.com/
Description: language definition for Apache configuration files (httpd.conf & .htaccess)
Version: 1.1
Date: 2008-12-27
*/

hljs.LANGUAGES.apache = function(){
  var NUMBER = {className: 'number', begin: '[\\$%]\\d+'};
  return {
    case_insensitive: true,
    defaultMode: {
      keywords: {
        keyword: 'acceptfilter acceptmutex acceptpathinfo accessfilename action addalt ' +
          'addaltbyencoding addaltbytype addcharset adddefaultcharset adddescription ' +
          'addencoding addhandler addicon addiconbyencoding addiconbytype addinputfilter ' +
          'addlanguage addmoduleinfo addoutputfilter addoutputfilterbytype addtype alias ' +
          'aliasmatch allow allowconnect allowencodedslashes allowoverride anonymous ' +
          'anonymous_logemail anonymous_mustgiveemail anonymous_nouserid anonymous_verifyemail ' +
          'authbasicauthoritative authbasicprovider authdbduserpwquery authdbduserrealmquery ' +
          'authdbmgroupfile authdbmtype authdbmuserfile authdefaultauthoritative ' +
          'authdigestalgorithm authdigestdomain authdigestnccheck authdigestnonceformat ' +
          'authdigestnoncelifetime authdigestprovider authdigestqop authdigestshmemsize ' +
          'authgroupfile authldapbinddn authldapbindpassword authldapcharsetconfig ' +
          'authldapcomparednonserver authldapdereferencealiases authldapgroupattribute ' +
          'authldapgroupattributeisdn authldapremoteuserattribute authldapremoteuserisdn ' +
          'authldapurl authname authnprovideralias authtype authuserfile authzdbmauthoritative ' +
          'authzdbmtype authzdefaultauthoritative authzgroupfileauthoritative ' +
          'authzldapauthoritative authzownerauthoritative authzuserauthoritative ' +
          'balancermember browsermatch browsermatchnocase bufferedlogs cachedefaultexpire ' +
          'cachedirlength cachedirlevels cachedisable cacheenable cachefile ' +
          'cacheignorecachecontrol cacheignoreheaders cacheignorenolastmod ' +
          'cacheignorequerystring cachelastmodifiedfactor cachemaxexpire cachemaxfilesize ' +
          'cacheminfilesize cachenegotiateddocs cacheroot cachestorenostore cachestoreprivate ' +
          'cgimapextension charsetdefault charsetoptions charsetsourceenc checkcaseonly ' +
          'checkspelling chrootdir contentdigest cookiedomain cookieexpires cookielog ' +
          'cookiename cookiestyle cookietracking coredumpdirectory customlog dav ' +
          'davdepthinfinity davgenericlockdb davlockdb davmintimeout dbdexptime dbdkeep ' +
          'dbdmax dbdmin dbdparams dbdpersist dbdpreparesql dbdriver defaulticon ' +
          'defaultlanguage defaulttype deflatebuffersize deflatecompressionlevel ' +
          'deflatefilternote deflatememlevel deflatewindowsize deny directoryindex ' +
          'directorymatch directoryslash documentroot dumpioinput dumpiologlevel dumpiooutput ' +
          'enableexceptionhook enablemmap enablesendfile errordocument errorlog example ' +
          'expiresactive expiresbytype expiresdefault extendedstatus extfilterdefine ' +
          'extfilteroptions fileetag filterchain filterdeclare filterprotocol filterprovider ' +
          'filtertrace forcelanguagepriority forcetype forensiclog gracefulshutdowntimeout ' +
          'group header headername hostnamelookups identitycheck identitychecktimeout ' +
          'imapbase imapdefault imapmenu include indexheadinsert indexignore indexoptions ' +
          'indexorderdefault indexstylesheet isapiappendlogtoerrors isapiappendlogtoquery ' +
          'isapicachefile isapifakeasync isapilognotsupported isapireadaheadbuffer keepalive ' +
          'keepalivetimeout languagepriority ldapcacheentries ldapcachettl ' +
          'ldapconnectiontimeout ldapopcacheentries ldapopcachettl ldapsharedcachefile ' +
          'ldapsharedcachesize ldaptrustedclientcert ldaptrustedglobalcert ldaptrustedmode ' +
          'ldapverifyservercert limitinternalrecursion limitrequestbody limitrequestfields ' +
          'limitrequestfieldsize limitrequestline limitxmlrequestbody listen listenbacklog ' +
          'loadfile loadmodule lockfile logformat loglevel maxclients maxkeepaliverequests ' +
          'maxmemfree maxrequestsperchild maxrequestsperthread maxspareservers maxsparethreads ' +
          'maxthreads mcachemaxobjectcount mcachemaxobjectsize mcachemaxstreamingbuffer ' +
          'mcacheminobjectsize mcacheremovalalgorithm mcachesize metadir metafiles metasuffix ' +
          'mimemagicfile minspareservers minsparethreads mmapfile mod_gzip_on ' +
          'mod_gzip_add_header_count mod_gzip_keep_workfiles mod_gzip_dechunk ' +
          'mod_gzip_min_http mod_gzip_minimum_file_size mod_gzip_maximum_file_size ' +
          'mod_gzip_maximum_inmem_size mod_gzip_temp_dir mod_gzip_item_include ' +
          'mod_gzip_item_exclude mod_gzip_command_version mod_gzip_can_negotiate ' +
          'mod_gzip_handle_methods mod_gzip_static_suffix mod_gzip_send_vary ' +
          'mod_gzip_update_static modmimeusepathinfo multiviewsmatch namevirtualhost noproxy ' +
          'nwssltrustedcerts nwsslupgradeable options order passenv pidfile protocolecho ' +
          'proxybadheader proxyblock proxydomain proxyerroroverride proxyftpdircharset ' +
          'proxyiobuffersize proxymaxforwards proxypass proxypassinterpolateenv ' +
          'proxypassmatch proxypassreverse proxypassreversecookiedomain ' +
          'proxypassreversecookiepath proxypreservehost proxyreceivebuffersize proxyremote ' +
          'proxyremotematch proxyrequests proxyset proxystatus proxytimeout proxyvia ' +
          'readmename receivebuffersize redirect redirectmatch redirectpermanent ' +
          'redirecttemp removecharset removeencoding removehandler removeinputfilter ' +
          'removelanguage removeoutputfilter removetype requestheader require rewritebase ' +
          'rewritecond rewriteengine rewritelock rewritelog rewriteloglevel rewritemap ' +
          'rewriteoptions rewriterule rlimitcpu rlimitmem rlimitnproc satisfy scoreboardfile ' +
          'script scriptalias scriptaliasmatch scriptinterpretersource scriptlog ' +
          'scriptlogbuffer scriptloglength scriptsock securelisten seerequesttail ' +
          'sendbuffersize serveradmin serveralias serverlimit servername serverpath ' +
          'serverroot serversignature servertokens setenv setenvif setenvifnocase sethandler ' +
          'setinputfilter setoutputfilter ssienableaccess ssiendtag ssierrormsg ssistarttag ' +
          'ssitimeformat ssiundefinedecho sslcacertificatefile sslcacertificatepath ' +
          'sslcadnrequestfile sslcadnrequestpath sslcarevocationfile sslcarevocationpath ' +
          'sslcertificatechainfile sslcertificatefile sslcertificatekeyfile sslciphersuite ' +
          'sslcryptodevice sslengine sslhonorciperorder sslmutex ssloptions ' +
          'sslpassphrasedialog sslprotocol sslproxycacertificatefile ' +
          'sslproxycacertificatepath sslproxycarevocationfile sslproxycarevocationpath ' +
          'sslproxyciphersuite sslproxyengine sslproxymachinecertificatefile ' +
          'sslproxymachinecertificatepath sslproxyprotocol sslproxyverify ' +
          'sslproxyverifydepth sslrandomseed sslrequire sslrequiressl sslsessioncache ' +
          'sslsessioncachetimeout sslusername sslverifyclient sslverifydepth startservers ' +
          'startthreads substitute suexecusergroup threadlimit threadsperchild ' +
          'threadstacksize timeout traceenable transferlog typesconfig unsetenv ' +
          'usecanonicalname usecanonicalphysicalport user userdir virtualdocumentroot ' +
          'virtualdocumentrootip virtualscriptalias virtualscriptaliasip ' +
          'win32disableacceptex xbithack',
        literal: 'on off'
      },
      contains: [
        hljs.HASH_COMMENT_MODE,
        {
          className: 'sqbracket',
          begin: '\\s\\[', end: '\\]$'
        },
        {
          className: 'cbracket',
          begin: '[\\$%]\\{', end: '\\}',
          contains: ['self', NUMBER]
        },
        NUMBER,
        {className: 'tag', begin: '</?', end: '>'},
        hljs.QUOTE_STRING_MODE
      ]
    }
  };
}();

/*
Language: Bash
Author: vah <vahtenberg@gmail.com>
*/

hljs.LANGUAGES.bash = function(){
  var BASH_LITERAL = 'true false';
  var VAR1 = {
    className: 'variable',
    begin: '\\$([a-zA-Z0-9_]+)\\b'
  };
  var VAR2 = {
    className: 'variable',
    begin: '\\$\\{(([^}])|(\\\\}))+\\}',
    contains: [hljs.C_NUMBER_MODE]
  };
  var QUOTE_STRING = {
    className: 'string',
    begin: '"', end: '"',
    illegal: '\\n',
    contains: [hljs.BACKSLASH_ESCAPE, VAR1, VAR2],
    relevance: 0
  };
  var APOS_STRING = {
    className: 'string',
    begin: '\'', end: '\'',
    contains: [{begin: '\'\''}],
    relevance: 0
  };
  var TEST_CONDITION = {
    className: 'test_condition',
    begin: '', end: '',
    contains: [QUOTE_STRING, APOS_STRING, VAR1, VAR2, hljs.C_NUMBER_MODE],
    keywords: {
      literal: BASH_LITERAL
    },
    relevance: 0
  };

  return {
    defaultMode: {
      keywords: {
        keyword: 'if then else fi for break continue while in do done echo exit return set declare',
        literal: BASH_LITERAL
      },
      contains: [
        {
          className: 'shebang',
          begin: '(#!\\/bin\\/bash)|(#!\\/bin\\/sh)',
          relevance: 10
        },
        VAR1,
        VAR2,
        hljs.HASH_COMMENT_MODE,
        hljs.C_NUMBER_MODE,
        QUOTE_STRING,
        APOS_STRING,
        hljs.inherit(TEST_CONDITION, {begin: '\\[ ', end: ' \\]', relevance: 0}),
        hljs.inherit(TEST_CONDITION, {begin: '\\[\\[ ', end: ' \\]\\]'})
      ]
    }
  };
}();

/*
Language: CoffeeScript
Author: Dmytrii Nagirniak <dnagir@gmail.com>
Contributors: Oleg Efimov <efimovov@gmail.com>
Description: CoffeeScript is a programming language that transcompiles to JavaScript. For info about language see http://coffeescript.org/
*/

hljs.LANGUAGES.coffeescript = function() {
  var keywords = {
    keyword:
      // JS keywords
      'in if for while finally new do return else break catch instanceof throw try this ' +
      'switch continue typeof delete debugger class extends super' +
      // Coffee keywords
      'then unless until loop of by when and or is isnt not',
    literal:
      // JS literals
      'true false null undefined ' +
      // Coffee literals
      'yes no on off ',
    reserved: 'case default function var void with const let enum export import native ' +
      '__hasProp __extends __slice __bind __indexOf'
  };

  var JS_IDENT_RE = '[A-Za-z$_][0-9A-Za-z$_]*';

  var COFFEE_QUOTE_STRING_SUBST_MODE = {
    className: 'subst',
    begin: '#\\{', end: '}',
    keywords: keywords,
    contains: [hljs.C_NUMBER_MODE, hljs.BINARY_NUMBER_MODE]
  };

  var COFFEE_QUOTE_STRING_MODE = {
    className: 'string',
    begin: '"', end: '"',
    relevance: 0,
    contains: [hljs.BACKSLASH_ESCAPE, COFFEE_QUOTE_STRING_SUBST_MODE]
  };

  var COFFEE_HEREDOC_MODE = {
    className: 'string',
    begin: '"""', end: '"""',
    contains: [hljs.BACKSLASH_ESCAPE, COFFEE_QUOTE_STRING_SUBST_MODE]
  };

  var COFFEE_HERECOMMENT_MODE = {
    className: 'comment',
    begin: '###', end: '###'
  };

  var COFFEE_HEREGEX_MODE = {
    className: 'regexp',
    begin: '///', end: '///',
    contains: [hljs.HASH_COMMENT_MODE]
  };

  var COFFEE_FUNCTION_DECLARATION_MODE = {
    className: 'function',
    begin: JS_IDENT_RE + '\\s*=\\s*(\\(.+\\))?\\s*[-=]>',
    returnBegin: true,
    contains: [
      {
        className: 'title',
        begin: JS_IDENT_RE
      },
      {
        className: 'params',
        begin: '\\(', end: '\\)'
      }
    ]
  };

  var COFFEE_EMBEDDED_JAVASCRIPT = {
    begin: '`', end: '`',
    excludeBegin: true, excludeEnd: true,
    subLanguage: 'javascript'
  };

  return {
    defaultMode: {
      keywords: keywords,
      contains: [
        // Numbers
        hljs.C_NUMBER_MODE,
        hljs.BINARY_NUMBER_MODE,
        // Strings
        hljs.APOS_STRING_MODE,
        COFFEE_HEREDOC_MODE, // Should be before COFFEE_QUOTE_STRING_MODE for greater priority
        COFFEE_QUOTE_STRING_MODE,
        // Comments
        COFFEE_HERECOMMENT_MODE, // Should be before hljs.HASH_COMMENT_MODE for greater priority
        hljs.HASH_COMMENT_MODE,
        // CoffeeScript specific modes
        COFFEE_HEREGEX_MODE,
        COFFEE_EMBEDDED_JAVASCRIPT,
        COFFEE_FUNCTION_DECLARATION_MODE
      ]
    }
  };
}();

/*
Language: C++
Contributors: Evgeny Stepanischev <imbolk@gmail.com>
*/

hljs.LANGUAGES.cpp = function(){
  var CPP_KEYWORDS = {
    keyword: 'false int float while private char catch export virtual operator sizeof ' +
      'dynamic_cast|10 typedef const_cast|10 const struct for static_cast|10 union namespace ' +
      'unsigned long throw volatile static protected bool template mutable if public friend ' +
      'do return goto auto void enum else break new extern using true class asm case typeid ' +
      'short reinterpret_cast|10 default double register explicit signed typename try this ' +
      'switch continue wchar_t inline delete alignof char16_t char32_t constexpr decltype ' +
      'noexcept nullptr static_assert thread_local restrict _Bool complex',
    built_in: 'std string cin cout cerr clog stringstream istringstream ostringstream ' +
      'auto_ptr deque list queue stack vector map set bitset multiset multimap unordered_set ' +
      'unordered_map unordered_multiset unordered_multimap array shared_ptr'
  };
  return {
    defaultMode: {
      keywords: CPP_KEYWORDS,
      illegal: '</',
      contains: [
        hljs.C_LINE_COMMENT_MODE,
        hljs.C_BLOCK_COMMENT_MODE,
        hljs.QUOTE_STRING_MODE,
        {
          className: 'string',
          begin: '\'\\\\?.', end: '\'',
          illegal: '.'
        },
        {
          className: 'number',
          begin: '\\b(\\d+(\\.\\d*)?|\\.\\d+)(u|U|l|L|ul|UL|f|F)'
        },
        hljs.C_NUMBER_MODE,
        {
          className: 'preprocessor',
          begin: '#', end: '$'
        },
        {
          className: 'stl_container',
          begin: '\\b(deque|list|queue|stack|vector|map|set|bitset|multiset|multimap|unordered_map|unordered_set|unordered_multiset|unordered_multimap|array)\\s*<', end: '>',
          keywords: CPP_KEYWORDS,
          relevance: 10,
          contains: ['self']
        }
      ]
    }
  };
}();


/*
Language: CSS
*/

hljs.LANGUAGES.css = function() {
  var FUNCTION = {
    className: 'function',
    begin: hljs.IDENT_RE + '\\(', end: '\\)',
    contains: [{
        endsWithParent: true, excludeEnd: true,
        contains: [hljs.NUMBER_MODE, hljs.APOS_STRING_MODE, hljs.QUOTE_STRING_MODE]
    }]
  };
  return {
    case_insensitive: true,
    defaultMode: {
      illegal: '[=/|\']',
      contains: [
        hljs.C_BLOCK_COMMENT_MODE,
        {
          className: 'id', begin: '\\#[A-Za-z0-9_-]+'
        },
        {
          className: 'class', begin: '\\.[A-Za-z0-9_-]+',
          relevance: 0
        },
        {
          className: 'attr_selector',
          begin: '\\[', end: '\\]',
          illegal: '$'
        },
        {
          className: 'pseudo',
          begin: ':(:)?[a-zA-Z0-9\\_\\-\\+\\(\\)\\"\\\']+'
        },
        {
          className: 'at_rule',
          begin: '@(font-face|page)',
          lexems: '[a-z-]+',
          keywords: 'font-face page'
        },
        {
          className: 'at_rule',
          begin: '@', end: '[{;]', // at_rule eating first "{" is a good thing
                                   // because it doesn’t let it to be parsed as
                                   // a rule set but instead drops parser into
                                   // the defaultMode which is how it should be.
          excludeEnd: true,
          keywords: 'import page media charset',
          contains: [
            FUNCTION,
            hljs.APOS_STRING_MODE, hljs.QUOTE_STRING_MODE,
            hljs.NUMBER_MODE
          ]
        },
        {
          className: 'tag', begin: hljs.IDENT_RE,
          relevance: 0
        },
        {
          className: 'rules',
          begin: '{', end: '}',
          illegal: '[^\\s]',
          relevance: 0,
          contains: [
            hljs.C_BLOCK_COMMENT_MODE,
            {
              className: 'rule',
              begin: '[^\\s]', returnBegin: true, end: ';', endsWithParent: true,
              contains: [
                {
                  className: 'attribute',
                  begin: '[A-Z\\_\\.\\-]+', end: ':',
                  excludeEnd: true,
                  illegal: '[^\\s]',
                  starts: {
                    className: 'value',
                    endsWithParent: true, excludeEnd: true,
                    contains: [
                      FUNCTION,
                      hljs.NUMBER_MODE,
                      hljs.QUOTE_STRING_MODE,
                      hljs.APOS_STRING_MODE,
                      hljs.C_BLOCK_COMMENT_MODE,
                      {
                        className: 'hexcolor', begin: '\\#[0-9A-F]+'
                      },
                      {
                        className: 'important', begin: '!important'
                      }
                    ]
                  }
                }
              ]
            }
          ]
        }
      ]
    }
  };
}();

/*
Language: Diff
Description: Unified and context diff
Author: Vasily Polovnyov <vast@whiteants.net>
*/

hljs.LANGUAGES.diff = {
  case_insensitive: true,
  defaultMode: {
    contains: [
      {
        className: 'chunk',
        begin: '^\\@\\@ +\\-\\d+,\\d+ +\\+\\d+,\\d+ +\\@\\@$',
        relevance: 10
      },
      {
        className: 'chunk',
        begin: '^\\*\\*\\* +\\d+,\\d+ +\\*\\*\\*\\*$',
        relevance: 10
      },
      {
        className: 'chunk',
        begin: '^\\-\\-\\- +\\d+,\\d+ +\\-\\-\\-\\-$',
        relevance: 10
      },
      {
        className: 'header',
        begin: 'Index: ', end: '$'
      },
      {
        className: 'header',
        begin: '=====', end: '=====$'
      },
      {
        className: 'header',
        begin: '^\\-\\-\\-', end: '$'
      },
      {
        className: 'header',
        begin: '^\\*{3} ', end: '$'
      },
      {
        className: 'header',
        begin: '^\\+\\+\\+', end: '$'
      },
      {
        className: 'header',
        begin: '\\*{5}', end: '\\*{5}$'
      },
      {
        className: 'addition',
        begin: '^\\+', end: '$'
      },
      {
        className: 'deletion',
        begin: '^\\-', end: '$'
      },
      {
        className: 'change',
        begin: '^\\!', end: '$'
      }
    ]
  }
};

/*
Language: Go
Author: Stephan Kountso aka StepLg <steplg@gmail.com>
Contributors: Evgeny Stepanischev <imbolk@gmail.com>
Description: Google go language (golang). For info about language see http://golang.org/
*/

hljs.LANGUAGES.go = function(){
  var GO_KEYWORDS = {
    keyword:
      'break default func interface select case map struct chan else goto package switch ' +
      'const fallthrough if range type continue for import return var go defer',
    constant:
       'true false iota nil',
    typename:
      'bool byte complex64 complex128 float32 float64 int8 int16 int32 int64 string uint8 ' +
      'uint16 uint32 uint64 int uint uintptr rune',
    built_in:
      'append cap close complex copy imag len make new panic print println real recover delete'
  };
  return {
    defaultMode: {
      keywords: GO_KEYWORDS,
      illegal: '</',
      contains: [
        hljs.C_LINE_COMMENT_MODE,
        hljs.C_BLOCK_COMMENT_MODE,
        hljs.QUOTE_STRING_MODE,
        {
          className: 'string',
          begin: '\'', end: '[^\\\\]\'',
          relevance: 0
        },
        {
          className: 'string',
          begin: '`', end: '`'
        },
        {
          className: 'number',
          begin: '[^a-zA-Z_0-9](\\-|\\+)?\\d+(\\.\\d+|\\/\\d+)?((d|e|f|l|s)(\\+|\\-)?\\d+)?',
          relevance: 0
        },
        hljs.C_NUMBER_MODE
      ]
    }
  };
}();

/*
Language: Haskell
Author: Jeremy Hull <sourdrums@gmail.com>
*/

hljs.LANGUAGES.haskell = function(){
  var LABEL = {
    className: 'label',
    begin: '\\b[A-Z][\\w\']*',
    relevance: 0
  };
  var CONTAINER = {
    className: 'container',
    begin: '\\(', end: '\\)',
    contains: [
      {className: 'label', begin: '\\b[A-Z][\\w\\(\\)\\.\']*'},
      {className: 'title', begin: '[_a-z][\\w\']*'}
    ]
  };

  return {
    defaultMode: {
      keywords:
        'let in if then else case of where do module import hiding qualified type data ' +
        'newtype deriving class instance null not as',
      contains: [
        {
          className: 'comment',
          begin: '--', end: '$'
        },
        {
          className: 'comment',
          begin: '{-', end: '-}'
        },
        {
          className: 'string',
          begin: '\\s+\'', end: '\'',
          contains: [hljs.BACKSLASH_ESCAPE],
          relevance: 0
        },
        hljs.QUOTE_STRING_MODE,
        {
          className: 'import',
          begin: '\\bimport', end: '$',
          keywords: 'import qualified as hiding',
          contains: [CONTAINER]
        },
        {
          className: 'module',
          begin: '\\bmodule', end: 'where',
          keywords: 'module where',
          contains: [CONTAINER]
        },
        {
          className: 'class',
          begin: '\\b(class|instance|data|(new)?type)', end: '(where|$)',
          keywords: 'class where instance data type newtype deriving',
          contains: [LABEL]
        },
        hljs.C_NUMBER_MODE,
        {
          className: 'shebang',
          begin: '#!\\/usr\\/bin\\/env\ runhaskell', end: '$'
        },
        LABEL,
        {
          className: 'title', begin: '^[_a-z][\\w\']*'
        }
      ]
    }
  };
}();

/*
Language: HTTP
Description: HTTP request and response headers with automatic body highlighting
Author: Ivan Sagalaev <maniac@softwaremaniacs.org>
*/

hljs.LANGUAGES.http = {
defaultMode: {
  illegal: '\\S',
  contains: [
    {
      className: 'status',
      begin: '^HTTP/[0-9\\.]+', end: '$',
      contains: [{className: 'number', begin: '\\b\\d{3}\\b'}]
    },
    {
      className: 'request',
      begin: '^[A-Z]+ (.*?) HTTP/[0-9\\.]+$', returnBegin: true, end: '$',
      contains: [
        {
          className: 'string',
          begin: ' ', end: ' ',
          excludeBegin: true, excludeEnd: true
        }
      ]
    },
    {
      className: 'attribute',
      begin: '^\\w', end: ': ', excludeEnd: true,
      illegal: '\\n',
      starts: {className: 'string', end: '$'}
    },
    {
      begin: '\\n\\n',
      starts: {subLanguage: '', endsWithParent: true}
    }
  ]
}
}

/*
Language: Ini
*/

hljs.LANGUAGES.ini = {
  case_insensitive: true,
  defaultMode: {
    illegal: '[^\\s]',
    contains: [
      {
        className: 'comment',
        begin: ';', end: '$'
      },
      {
        className: 'title',
        begin: '^\\[', end: '\\]'
      },
      {
        className: 'setting',
        begin: '^[a-z0-9_\\[\\]]+[ \\t]*=[ \\t]*', end: '$',
        contains: [
          {
            className: 'value',
            endsWithParent: true,
            keywords: 'on off true false yes no',
            contains: [hljs.QUOTE_STRING_MODE, hljs.NUMBER_MODE]
          }
        ]
      }
    ]
  }
};

/*
Language: Java
Author: Vsevolod Solovyov <vsevolod.solovyov@gmail.com>
*/

hljs.LANGUAGES.java  = {
  defaultMode: {
    keywords:
      'false synchronized int abstract float private char boolean static null if const ' +
      'for true while long throw strictfp finally protected import native final return void ' +
      'enum else break transient new catch instanceof byte super volatile case assert short ' +
      'package default double public try this switch continue throws',
    contains: [
      {
        className: 'javadoc',
        begin: '/\\*\\*', end: '\\*/',
        contains: [{
          className: 'javadoctag', begin: '@[A-Za-z]+'
        }],
        relevance: 10
      },
      hljs.C_LINE_COMMENT_MODE,
      hljs.C_BLOCK_COMMENT_MODE,
      hljs.APOS_STRING_MODE,
      hljs.QUOTE_STRING_MODE,
      {
        className: 'class',
        beginWithKeyword: true, end: '{',
        keywords: 'class interface',
        illegal: ':',
        contains: [
          {
            beginWithKeyword: true,
            keywords: 'extends implements',
            relevance: 10
          },
          {
            className: 'title',
            begin: hljs.UNDERSCORE_IDENT_RE
          }
        ]
      },
      hljs.C_NUMBER_MODE,
      {
        className: 'annotation', begin: '@[A-Za-z]+'
      }
    ]
  }
};

/*
Language: JavaScript
*/

hljs.LANGUAGES.javascript = {
  defaultMode: {
    keywords: {
      keyword:
        'in if for while finally var new function do return void else break catch ' +
        'instanceof with throw case default try this switch continue typeof delete',
      literal:
        'true false null undefined NaN Infinity'
    },
    contains: [
      hljs.APOS_STRING_MODE,
      hljs.QUOTE_STRING_MODE,
      hljs.C_LINE_COMMENT_MODE,
      hljs.C_BLOCK_COMMENT_MODE,
      hljs.C_NUMBER_MODE,
      { // regexp container
        begin: '(' + hljs.RE_STARTERS_RE + '|\\b(case|return|throw)\\b)\\s*',
        keywords: 'return throw case',
        contains: [
          hljs.C_LINE_COMMENT_MODE,
          hljs.C_BLOCK_COMMENT_MODE,
          {
            className: 'regexp',
            begin: '/', end: '/[gim]*',
            contains: [{begin: '\\\\/'}]
          }
        ],
        relevance: 0
      },
      {
        className: 'function',
        beginWithKeyword: true, end: '{',
        keywords: 'function',
        contains: [
          {
            className: 'title', begin: '[A-Za-z$_][0-9A-Za-z$_]*'
          },
          {
            className: 'params',
            begin: '\\(', end: '\\)',
            contains: [
              hljs.C_LINE_COMMENT_MODE,
              hljs.C_BLOCK_COMMENT_MODE
            ],
            illegal: '["\'\\(]'
          }
        ]
      }
    ]
  }
};

/*
Language: JSON
Author: Ivan Sagalaev <maniac@softwaremaniacs.org>
*/

hljs.LANGUAGES.json = function(){
  var LITERALS = {literal: 'true false null'};
  var TYPES = [
    hljs.QUOTE_STRING_MODE,
    hljs.C_NUMBER_MODE
  ];
  var VALUE_CONTAINER = {
    className: 'value',
    end: ',', endsWithParent: true, excludeEnd: true,
    contains: TYPES,
    keywords: LITERALS
  };
  var OBJECT = {
    begin: '{', end: '}',
    contains: [
      {
        className: 'attribute',
        begin: '\\s*"', end: '"\\s*:\\s*', excludeBegin: true, excludeEnd: true,
        contains: [hljs.BACKSLASH_ESCAPE],
        illegal: '\\n',
        starts: VALUE_CONTAINER
      }
    ],
    illegal: '\\S'
  };
  var ARRAY = {
    begin: '\\[', end: '\\]',
    contains: [hljs.inherit(VALUE_CONTAINER, {className: null})], // inherit is also a workaround for a bug that makes shared modes with endsWithParent compile only the ending of one of the parents
    illegal: '\\S'
  };
  TYPES.splice(TYPES.length, 0, OBJECT, ARRAY);
  return {
    defaultMode: {
      contains: TYPES,
      keywords: LITERALS,
      illegal: '\\S'
    }
  };
}();

/*
Language: Lisp
Description: Generic lisp syntax
Author: Vasily Polovnyov <vast@whiteants.net>
*/

hljs.LANGUAGES.lisp = function(){
  var LISP_IDENT_RE = '[a-zA-Z_\\-\\+\\*\\/\\<\\=\\>\\&\\#][a-zA-Z0-9_\\-\\+\\*\\/\\<\\=\\>\\&\\#]*';
  var LISP_SIMPLE_NUMBER_RE = '(\\-|\\+)?\\d+(\\.\\d+|\\/\\d+)?((d|e|f|l|s)(\\+|\\-)?\\d+)?';
  var LITERAL = {
    className: 'literal',
    begin: '\\b(t{1}|nil)\\b'
  };
  var NUMBERS = [
    {
      className: 'number', begin: LISP_SIMPLE_NUMBER_RE
    },
    {
      className: 'number', begin: '#b[0-1]+(/[0-1]+)?'
    },
    {
      className: 'number', begin: '#o[0-7]+(/[0-7]+)?'
    },
    {
      className: 'number', begin: '#x[0-9a-f]+(/[0-9a-f]+)?'
    },
    {
      className: 'number', begin: '#c\\(' + LISP_SIMPLE_NUMBER_RE + ' +' + LISP_SIMPLE_NUMBER_RE, end: '\\)'
    }
  ]
  var STRING = {
    className: 'string',
    begin: '"', end: '"',
    contains: [hljs.BACKSLASH_ESCAPE],
    relevance: 0
  };
  var COMMENT = {
    className: 'comment',
    begin: ';', end: '$'
  };
  var VARIABLE = {
    className: 'variable',
    begin: '\\*', end: '\\*'
  };
  var KEYWORD = {
    className: 'keyword',
    begin: '[:&]' + LISP_IDENT_RE
  };
  var QUOTED_LIST = {
    begin: '\\(', end: '\\)',
    contains: ['self', LITERAL, STRING].concat(NUMBERS)
  };
  var QUOTED1 = {
    className: 'quoted',
    begin: '[\'`]\\(', end: '\\)',
    contains: NUMBERS.concat([STRING, VARIABLE, KEYWORD, QUOTED_LIST])
  };
  var QUOTED2 = {
    className: 'quoted',
    begin: '\\(quote ', end: '\\)',
    keywords: {title: 'quote'},
    contains: NUMBERS.concat([STRING, VARIABLE, KEYWORD, QUOTED_LIST])
  };
  var LIST = {
    className: 'list',
    begin: '\\(', end: '\\)'
  };
  var BODY = {
    className: 'body',
    endsWithParent: true, excludeEnd: true
  };
  LIST.contains = [{className: 'title', begin: LISP_IDENT_RE}, BODY];
  BODY.contains = [QUOTED1, QUOTED2, LIST, LITERAL].concat(NUMBERS).concat([STRING, COMMENT, VARIABLE, KEYWORD]);

  return {
    case_insensitive: true,
    defaultMode: {
      illegal: '[^\\s]',
      contains: NUMBERS.concat([
        LITERAL,
        STRING,
        COMMENT,
        QUOTED1, QUOTED2,
        LIST
      ])
    }
  };
}();

/*
Language: Lua
Author: Andrew Fedorov <dmmdrs@mail.ru>
*/

hljs.LANGUAGES.lua = function() {
  var OPENING_LONG_BRACKET = '\\[=*\\[';
  var CLOSING_LONG_BRACKET = '\\]=*\\]';
  var LONG_BRACKETS = {
    begin: OPENING_LONG_BRACKET, end: CLOSING_LONG_BRACKET,
    contains: ['self']
  };
  var COMMENTS = [
    {
      className: 'comment',
      begin: '--(?!' + OPENING_LONG_BRACKET + ')', end: '$'
    },
    {
      className: 'comment',
      begin: '--' + OPENING_LONG_BRACKET, end: CLOSING_LONG_BRACKET,
      contains: [LONG_BRACKETS],
      relevance: 10
    }
  ]
  return {
    defaultMode: {
      lexems: hljs.UNDERSCORE_IDENT_RE,
      keywords: {
        keyword:
          'and break do else elseif end false for if in local nil not or repeat return then ' +
          'true until while',
        built_in:
          '_G _VERSION assert collectgarbage dofile error getfenv getmetatable ipairs load ' +
          'loadfile loadstring module next pairs pcall print rawequal rawget rawset require ' +
          'select setfenv setmetatable tonumber tostring type unpack xpcall coroutine debug ' +
          'io math os package string table'
      },
      contains: COMMENTS.concat([
        {
          className: 'function',
          beginWithKeyword: true, end: '\\)',
          keywords: 'function',
          contains: [
            {
              className: 'title',
              begin: '([_a-zA-Z]\\w*\\.)*([_a-zA-Z]\\w*:)?[_a-zA-Z]\\w*'
            },
            {
              className: 'params',
              begin: '\\(', endsWithParent: true,
              contains: COMMENTS
            }
          ].concat(COMMENTS)
        },
        hljs.C_NUMBER_MODE,
        hljs.APOS_STRING_MODE,
        hljs.QUOTE_STRING_MODE,
        {
          className: 'string',
          begin: OPENING_LONG_BRACKET, end: CLOSING_LONG_BRACKET,
          contains: [LONG_BRACKETS],
          relevance: 10
        }
      ])
    }
  };
}();

/*
Language: Markdown
Requires: xml.js
Author: John Crepezzi <john.crepezzi@gmail.com>
Website: http://seejohncode.com/
*/

hljs.LANGUAGES.markdown = {
  case_insensitive: true,
  defaultMode: {
    contains: [
      // highlight headers
      {
        className: 'header',
        begin: '^#{1,3}', end: '$'
      },
      {
        className: 'header',
        begin: '^.+?\\n[=-]{2,}$'
      },
      // inline html
      {
        begin: '<', end: '>',
        subLanguage: 'xml',
        relevance: 0
      },
      // lists (indicators only)
      {
        className: 'bullet',
        begin: '^([*+-]|(\\d+\\.))\\s+'
      },
      // strong segments
      {
        className: 'strong',
        begin: '[*_]{2}.+?[*_]{2}'
      },
      // emphasis segments
      {
        className: 'emphasis',
        begin: '\\*.+?\\*'
      },
      {
        className: 'emphasis',
        begin: '_.+?_',
        relevance: 0
      },
      // blockquotes
      {
        className: 'blockquote',
        begin: '^>\\s+', end: '$'
      },
      // code snippets
      {
        className: 'code',
        begin: '`.+?`'
      },
      {
        className: 'code',
        begin: '^    ', end: '$',
        relevance: 0
      },
      // horizontal rules
      {
        className: 'horizontal_rule',
        begin: '^-{3,}', end: '$'
      },
      // using links - title and link
      {
        begin: '\\[.+?\\]\\(.+?\\)',
        returnBegin: true,
        contains: [
          {
            className: 'link_label',
            begin: '\\[.+\\]'
          },
          {
            className: 'link_url',
            begin: '\\(', end: '\\)',
            excludeBegin: true, excludeEnd: true
          }
        ]
      }
    ]
  }
};

/*
Language: Matlab
Author: Denis Bardadym <bardadymchik@gmail.com>
*/

hljs.LANGUAGES.matlab = {
  defaultMode: {
    keywords: {
      keyword:
        'break case catch classdef continue else elseif end enumerated events for function ' +
        'global if methods otherwise parfor persistent properties return spmd switch try while',
      built_in:
        'sin sind sinh asin asind asinh cos cosd cosh acos acosd acosh tan tand tanh atan ' +
        'atand atan2 atanh sec secd sech asec asecd asech csc cscd csch acsc acscd acsch cot ' +
        'cotd coth acot acotd acoth hypot exp expm1 log log1p log10 log2 pow2 realpow reallog ' +
        'realsqrt sqrt nthroot nextpow2 abs angle complex conj imag real unwrap isreal ' +
        'cplxpair fix floor ceil round mod rem sign airy besselj bessely besselh besseli ' +
        'besselk beta betainc betaln ellipj ellipke erf erfc erfcx erfinv expint gamma ' +
        'gammainc gammaln psi legendre cross dot factor isprime primes gcd lcm rat rats perms ' +
        'nchoosek factorial cart2sph cart2pol pol2cart sph2cart hsv2rgb rgb2hsv zeros ones ' +
        'eye repmat rand randn linspace logspace freqspace meshgrid accumarray size length ' +
        'ndims numel disp isempty isequal isequalwithequalnans cat reshape diag blkdiag tril ' +
        'triu fliplr flipud flipdim rot90 find sub2ind ind2sub bsxfun ndgrid permute ipermute ' +
        'shiftdim circshift squeeze isscalar isvector ans eps realmax realmin pi i inf nan ' +
        'isnan isinf isfinite j why compan gallery hadamard hankel hilb invhilb magic pascal ' +
        'rosser toeplitz vander wilkinson'
    },
    illegal: '(//|"|#|/\\*|\\s+/\\w+)',
    contains: [
      {
        className: 'function',
        beginWithKeyword: true, end: '$',
        keywords: 'function',
        contains: [
          {
              className: 'title',
              begin: hljs.UNDERSCORE_IDENT_RE
          },
          {
              className: 'params',
              begin: '\\(', end: '\\)'
          },
          {
              className: 'params',
              begin: '\\[', end: '\\]'
          }
        ]
      },
      {
        className: 'string',
        begin: '\'', end: '\'',
        contains: [hljs.BACKSLASH_ESCAPE, {begin: '\'\''}],
        relevance: 0
      },
      {
        className: 'comment',
        begin: '\\%', end: '$'
      },
      hljs.C_NUMBER_MODE
    ]
  }
};

/*
Language: Nginx
Author: Peter Leonov <gojpeg@yandex.ru>
*/

hljs.LANGUAGES.nginx = function() {
  var VAR1 = {
    className: 'variable',
    begin: '\\$\\d+'
  };
  var VAR2 = {
    className: 'variable',
    begin: '\\${', end: '}'
  };
  var VAR3 = {
    className: 'variable',
    begin: '[\\$\\@]' + hljs.UNDERSCORE_IDENT_RE
  };

  return {
    defaultMode: {
      contains: [
        hljs.HASH_COMMENT_MODE,
        { // directive
          begin: hljs.UNDERSCORE_IDENT_RE, end: ';|{', returnEnd: true,
          keywords:
            'accept_mutex accept_mutex_delay access_log add_after_body add_before_body ' +
            'add_header addition_types alias allow ancient_browser ancient_browser_value ' +
            'auth_basic auth_basic_user_file autoindex autoindex_exact_size ' +
            'autoindex_localtime break charset charset_map charset_types ' +
            'client_body_buffer_size client_body_in_file_only client_body_in_single_buffer ' +
            'client_body_temp_path client_body_timeout client_header_buffer_size ' +
            'client_header_timeout client_max_body_size connection_pool_size connections ' +
            'create_full_put_path daemon dav_access dav_methods debug_connection ' +
            'debug_points default_type deny directio directio_alignment echo echo_after_body ' +
            'echo_before_body echo_blocking_sleep echo_duplicate echo_end echo_exec ' +
            'echo_flush echo_foreach_split echo_location echo_location_async ' +
            'echo_read_request_body echo_request_body echo_reset_timer echo_sleep ' +
            'echo_subrequest echo_subrequest_async empty_gif env error_log error_page events ' +
            'expires fastcgi_bind fastcgi_buffer_size fastcgi_buffers ' +
            'fastcgi_busy_buffers_size fastcgi_cache fastcgi_cache_key fastcgi_cache_methods ' +
            'fastcgi_cache_min_uses fastcgi_cache_path fastcgi_cache_use_stale ' +
            'fastcgi_cache_valid fastcgi_catch_stderr fastcgi_connect_timeout ' +
            'fastcgi_hide_header fastcgi_ignore_client_abort fastcgi_ignore_headers ' +
            'fastcgi_index fastcgi_intercept_errors fastcgi_max_temp_file_size ' +
            'fastcgi_next_upstream fastcgi_param fastcgi_pass fastcgi_pass_header ' +
            'fastcgi_pass_request_body fastcgi_pass_request_headers fastcgi_read_timeout ' +
            'fastcgi_send_lowat fastcgi_send_timeout fastcgi_split_path_info fastcgi_store ' +
            'fastcgi_store_access fastcgi_temp_file_write_size fastcgi_temp_path ' +
            'fastcgi_upstream_fail_timeout fastcgi_upstream_max_fails flv geo geoip_city ' +
            'geoip_country gzip gzip_buffers gzip_comp_level gzip_disable gzip_hash ' +
            'gzip_http_version gzip_min_length gzip_no_buffer gzip_proxied gzip_static ' +
            'gzip_types gzip_vary gzip_window http if if_modified_since ' +
            'ignore_invalid_headers image_filter image_filter_buffer ' +
            'image_filter_jpeg_quality image_filter_transparency include index internal ' +
            'ip_hash js js_load js_require js_utf8 keepalive_requests keepalive_timeout ' +
            'kqueue_changes kqueue_events large_client_header_buffers limit_conn ' +
            'limit_conn_log_level limit_except limit_rate limit_rate_after limit_req ' +
            'limit_req_log_level limit_req_zone limit_zone lingering_time lingering_timeout ' +
            'listen location lock_file log_format log_not_found log_subrequest map ' +
            'map_hash_bucket_size map_hash_max_size master_process memcached_bind ' +
            'memcached_buffer_size memcached_connect_timeout memcached_next_upstream ' +
            'memcached_pass memcached_read_timeout memcached_send_timeout ' +
            'memcached_upstream_fail_timeout memcached_upstream_max_fails merge_slashes ' +
            'min_delete_depth modern_browser modern_browser_value more_clear_headers ' +
            'more_clear_input_headers more_set_headers more_set_input_headers msie_padding ' +
            'msie_refresh multi_accept open_file_cache open_file_cache_errors ' +
            'open_file_cache_events open_file_cache_min_uses open_file_cache_retest ' +
            'open_file_cache_valid open_log_file_cache optimize_server_names output_buffers ' +
            'override_charset perl perl_modules perl_require perl_set pid port_in_redirect ' +
            'post_action postpone_gzipping postpone_output proxy_bind proxy_buffer_size ' +
            'proxy_buffering proxy_buffers proxy_busy_buffers_size proxy_cache ' +
            'proxy_cache_key proxy_cache_methods proxy_cache_min_uses proxy_cache_path ' +
            'proxy_cache_use_stale proxy_cache_valid proxy_connect_timeout ' +
            'proxy_headers_hash_bucket_size proxy_headers_hash_max_size proxy_hide_header ' +
            'proxy_ignore_client_abort proxy_ignore_headers proxy_intercept_errors ' +
            'proxy_max_temp_file_size proxy_method proxy_next_upstream proxy_pass ' +
            'proxy_pass_header proxy_pass_request_body proxy_pass_request_headers ' +
            'proxy_read_timeout proxy_redirect proxy_send_lowat proxy_send_timeout ' +
            'proxy_set_body proxy_set_header proxy_store proxy_store_access ' +
            'proxy_temp_file_write_size proxy_temp_path proxy_upstream_fail_timeout ' +
            'proxy_upstream_max_fails push_authorized_channels_only push_channel_group ' +
            'push_max_channel_id_length push_max_channel_subscribers ' +
            'push_max_message_buffer_length push_max_reserved_memory ' +
            'push_message_buffer_length push_message_timeout push_min_message_buffer_length ' +
            'push_min_message_recipients push_publisher push_store_messages push_subscriber ' +
            'push_subscriber_concurrency random_index read_ahead real_ip_header ' +
            'recursive_error_pages request_pool_size reset_timedout_connection resolver ' +
            'resolver_timeout return rewrite rewrite_log root satisfy satisfy_any ' +
            'send_lowat send_timeout sendfile sendfile_max_chunk server server_name ' +
            'server_name_in_redirect server_names_hash_bucket_size server_names_hash_max_size ' +
            'server_tokens set set_real_ip_from source_charset ssi ' +
            'ssi_ignore_recycled_buffers ssi_min_file_chunk ssi_silent_errors ssi_types ' +
            'ssi_value_length ssl ssl_certificate ssl_certificate_key ssl_ciphers ' +
            'ssl_client_certificate ssl_crl ssl_dhparam ssl_prefer_server_ciphers ' +
            'ssl_protocols ssl_session_cache ssl_session_timeout ssl_verify_client ' +
            'ssl_verify_depth sub_filter sub_filter_once sub_filter_types tcp_nodelay ' +
            'tcp_nopush timer_resolution try_files types types_hash_bucket_size ' +
            'types_hash_max_size underscores_in_headers uninitialized_variable_warn upstream ' +
            'use user userid userid_domain userid_expires userid_mark userid_name userid_p3p ' +
            'userid_path userid_service valid_referers variables_hash_bucket_size ' +
            'variables_hash_max_size worker_connections worker_cpu_affinity worker_priority ' +
            'worker_processes worker_rlimit_core worker_rlimit_nofile ' +
            'worker_rlimit_sigpending working_directory xml_entities xslt_stylesheet xslt_types',
          relevance: 0,
          contains: [
            hljs.HASH_COMMENT_MODE,
            {
              begin: '\\s', end: '[;{]', returnBegin: true, returnEnd: true,
              lexems: '[a-z/]+',
              keywords: {
                built_in:
                  'on off yes no true false none blocked debug info notice warn error crit ' +
                  'select permanent redirect kqueue rtsig epoll poll /dev/poll'
              },
              relevance: 0,
              contains: [
                hljs.HASH_COMMENT_MODE,
                {
                  className: 'string',
                  begin: '"', end: '"',
                  contains: [hljs.BACKSLASH_ESCAPE, VAR1, VAR2, VAR3],
                  relevance: 0
                },
                {
                  className: 'string',
                  begin: "'", end: "'",
                  contains: [hljs.BACKSLASH_ESCAPE, VAR1, VAR2, VAR3],
                  relevance: 0
                },
                {
                  className: 'string',
                  begin: '([a-z]+):/', end: '[;\\s]', returnEnd: true
                },
                {
                  className: 'regexp',
                  begin: "\\s\\^", end: "\\s|{|;", returnEnd: true,
                  contains: [hljs.BACKSLASH_ESCAPE, VAR1, VAR2, VAR3]
                },
                // regexp locations (~, ~*)
                {
                  className: 'regexp',
                  begin: "~\\*?\\s+", end: "\\s|{|;", returnEnd: true,
                  contains: [hljs.BACKSLASH_ESCAPE, VAR1, VAR2, VAR3]
                },
                // *.example.com
                {
                  className: 'regexp',
                  begin: "\\*(\\.[a-z\\-]+)+",
                  contains: [hljs.BACKSLASH_ESCAPE, VAR1, VAR2, VAR3]
                },
                // sub.example.*
                {
                  className: 'regexp',
                  begin: "([a-z\\-]+\\.)+\\*",
                  contains: [hljs.BACKSLASH_ESCAPE, VAR1, VAR2, VAR3]
                },
                // IP
                {
                  className: 'number',
                  begin: '\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b'
                },
                // units
                {
                  className: 'number',
                  begin: '\\s\\d+[kKmMgGdshdwy]*\\b',
                  relevance: 0
                },
                VAR1, VAR2, VAR3
              ]
            }
          ]
        }
      ]
    }
  }
}();

/*
Language: Objective C
Author: Valerii Hiora <valerii.hiora@gmail.com>
Contributors: Angel G. Olloqui <angelgarcia.mail@gmail.com>
*/

hljs.LANGUAGES.objectivec = function(){
  var OBJC_KEYWORDS = {
    keyword:
      'int float while private char catch export sizeof typedef const struct for union ' +
      'unsigned long volatile static protected bool mutable if public do return goto void ' +
      'enum else break extern class asm case short default double throw register explicit ' +
      'signed typename try this switch continue wchar_t inline readonly assign property ' +
      'protocol self synchronized end synthesize id optional required implementation ' +
      'nonatomic interface super unichar finally dynamic IBOutlet IBAction selector strong ' +
      'weak readonly',
    literal:
    	'false true FALSE TRUE nil YES NO NULL',
    built_in:
      'NSString NSDictionary CGRect CGPoint UIButton UILabel UITextView UIWebView MKMapView ' +
      'UISegmentedControl NSObject UITableViewDelegate UITableViewDataSource NSThread ' +
      'UIActivityIndicator UITabbar UIToolBar UIBarButtonItem UIImageView NSAutoreleasePool ' +
      'UITableView BOOL NSInteger CGFloat NSException NSLog NSMutableString NSMutableArray ' +
      'NSMutableDictionary NSURL NSIndexPath CGSize UITableViewCell UIView UIViewController ' +
      'UINavigationBar UINavigationController UITabBarController UIPopoverController ' +
      'UIPopoverControllerDelegate UIImage NSNumber UISearchBar NSFetchedResultsController ' +
      'NSFetchedResultsChangeType UIScrollView UIScrollViewDelegate UIEdgeInsets UIColor ' +
      'UIFont UIApplication NSNotFound NSNotificationCenter NSNotification ' +
      'UILocalNotification NSBundle NSFileManager NSTimeInterval NSDate NSCalendar ' +
      'NSUserDefaults UIWindow NSRange NSArray NSError NSURLRequest NSURLConnection class ' +
      'UIInterfaceOrientation MPMoviePlayerController dispatch_once_t ' +
      'dispatch_queue_t dispatch_sync dispatch_async dispatch_once'
  };
  return {
    defaultMode: {
      keywords: OBJC_KEYWORDS,
      illegal: '</',
      contains: [
        hljs.C_LINE_COMMENT_MODE,
        hljs.C_BLOCK_COMMENT_MODE,
        hljs.C_NUMBER_MODE,
        hljs.QUOTE_STRING_MODE,
        {
          className: 'string',
          begin: '\'',
          end: '[^\\\\]\'',
          illegal: '[^\\\\][^\']'
        },

        {
          className: 'preprocessor',
          begin: '#import',
          end: '$',
          contains: [
          {
            className: 'title',
            begin: '\"',
            end: '\"'
          },
          {
            className: 'title',
            begin: '<',
            end: '>'
          }
          ]
        },
        {
          className: 'preprocessor',
          begin: '#',
          end: '$'
        },
        {
          className: 'class',
          beginWithKeyword: true,
          end: '({|$)',
          keywords: 'interface class protocol implementation',
          contains: [{
            className: 'id',
            begin: hljs.UNDERSCORE_IDENT_RE
          }
          ]
        },
        {
          className: 'variable',
          begin: '\\.'+hljs.UNDERSCORE_IDENT_RE
        }
      ]
    }
  };
}();

/*
Language: Perl
Author: Peter Leonov <gojpeg@yandex.ru>
*/

hljs.LANGUAGES.perl = function(){
  var PERL_KEYWORDS = 'getpwent getservent quotemeta msgrcv scalar kill dbmclose undef lc ' +
    'ma syswrite tr send umask sysopen shmwrite vec qx utime local oct semctl localtime ' +
    'readpipe do return format read sprintf dbmopen pop getpgrp not getpwnam rewinddir qq' +
    'fileno qw endprotoent wait sethostent bless s|0 opendir continue each sleep endgrent ' +
    'shutdown dump chomp connect getsockname die socketpair close flock exists index shmget' +
    'sub for endpwent redo lstat msgctl setpgrp abs exit select print ref gethostbyaddr ' +
    'unshift fcntl syscall goto getnetbyaddr join gmtime symlink semget splice x|0 ' +
    'getpeername recv log setsockopt cos last reverse gethostbyname getgrnam study formline ' +
    'endhostent times chop length gethostent getnetent pack getprotoent getservbyname rand ' +
    'mkdir pos chmod y|0 substr endnetent printf next open msgsnd readdir use unlink ' +
    'getsockopt getpriority rindex wantarray hex system getservbyport endservent int chr ' +
    'untie rmdir prototype tell listen fork shmread ucfirst setprotoent else sysseek link ' +
    'getgrgid shmctl waitpid unpack getnetbyname reset chdir grep split require caller ' +
    'lcfirst until warn while values shift telldir getpwuid my getprotobynumber delete and ' +
    'sort uc defined srand accept package seekdir getprotobyname semop our rename seek if q|0 ' +
    'chroot sysread setpwent no crypt getc chown sqrt write setnetent setpriority foreach ' +
    'tie sin msgget map stat getlogin unless elsif truncate exec keys glob tied closedir' +
    'ioctl socket readlink eval xor readline binmode setservent eof ord bind alarm pipe ' +
    'atan2 getgrent exp time push setgrent gt lt or ne m|0';
  var SUBST = {
    className: 'subst',
    begin: '[$@]\\{', end: '\\}',
    keywords: PERL_KEYWORDS,
    relevance: 10
  };
  var VAR1 = {
    className: 'variable',
    begin: '\\$\\d'
  };
  var VAR2 = {
    className: 'variable',
    begin: '[\\$\\%\\@\\*](\\^\\w\\b|#\\w+(\\:\\:\\w+)*|[^\\s\\w{]|{\\w+}|\\w+(\\:\\:\\w*)*)'
  };
  var STRING_CONTAINS = [hljs.BACKSLASH_ESCAPE, SUBST, VAR1, VAR2];
  var METHOD = {
    begin: '->',
    contains: [
      {begin: hljs.IDENT_RE},
      {begin: '{', end: '}'}
    ]
  };
  var COMMENT = {
    className: 'comment',
    begin: '^(__END__|__DATA__)', end: '\\n$',
    relevance: 5
  }
  var PERL_DEFAULT_CONTAINS = [
    VAR1, VAR2,
    hljs.HASH_COMMENT_MODE,
    COMMENT,
    {
      className: 'comment',
      begin: '^\\=\\w', end: '\\=cut', endsWithParent: true
    },
    METHOD,
    {
      className: 'string',
      begin: 'q[qwxr]?\\s*\\(', end: '\\)',
      contains: STRING_CONTAINS,
      relevance: 5
    },
    {
      className: 'string',
      begin: 'q[qwxr]?\\s*\\[', end: '\\]',
      contains: STRING_CONTAINS,
      relevance: 5
    },
    {
      className: 'string',
      begin: 'q[qwxr]?\\s*\\{', end: '\\}',
      contains: STRING_CONTAINS,
      relevance: 5
    },
    {
      className: 'string',
      begin: 'q[qwxr]?\\s*\\|', end: '\\|',
      contains: STRING_CONTAINS,
      relevance: 5
    },
    {
      className: 'string',
      begin: 'q[qwxr]?\\s*\\<', end: '\\>',
      contains: STRING_CONTAINS,
      relevance: 5
    },
    {
      className: 'string',
      begin: 'qw\\s+q', end: 'q',
      contains: STRING_CONTAINS,
      relevance: 5
    },
    {
      className: 'string',
      begin: '\'', end: '\'',
      contains: [hljs.BACKSLASH_ESCAPE],
      relevance: 0
    },
    {
      className: 'string',
      begin: '"', end: '"',
      contains: STRING_CONTAINS,
      relevance: 0
    },
    {
      className: 'string',
      begin: '`', end: '`',
      contains: [hljs.BACKSLASH_ESCAPE]
    },
    {
      className: 'string',
      begin: '{\\w+}',
      relevance: 0
    },
    {
      className: 'string',
      begin: '\-?\\w+\\s*\\=\\>',
      relevance: 0
    },
    {
      className: 'number',
      begin: '(\\b0[0-7_]+)|(\\b0x[0-9a-fA-F_]+)|(\\b[1-9][0-9_]*(\\.[0-9_]+)?)|[0_]\\b',
      relevance: 0
    },
    { // regexp container
      begin: '(' + hljs.RE_STARTERS_RE + '|\\b(split|return|print|reverse|grep)\\b)\\s*',
      keywords: 'split return print reverse grep',
      relevance: 0,
      contains: [
        hljs.HASH_COMMENT_MODE,
        COMMENT,
        {
          className: 'regexp',
          begin: '(s|tr|y)/(\\\\.|[^/])*/(\\\\.|[^/])*/[a-z]*',
          relevance: 10
        },
        {
          className: 'regexp',
          begin: '(m|qr)?/', end: '/[a-z]*',
          contains: [hljs.BACKSLASH_ESCAPE],
          relevance: 0 // allows empty "//" which is a common comment delimiter in other languages
        }
      ]
    },
    {
      className: 'sub',
      beginWithKeyword: true, end: '(\\s*\\(.*?\\))?[;{]',
      keywords: 'sub',
      relevance: 5
    },
    {
      className: 'operator',
      begin: '-\\w\\b',
      relevance: 0
    }
  ];
  SUBST.contains = PERL_DEFAULT_CONTAINS;
  METHOD.contains[1].contains = PERL_DEFAULT_CONTAINS;

  return {
    defaultMode: {
      keywords: PERL_KEYWORDS,
      contains: PERL_DEFAULT_CONTAINS
    }
  };
}();

/*
Language: PHP
Author: Victor Karamzin <Victor.Karamzin@enterra-inc.com>
Contributors: Evgeny Stepanischev <imbolk@gmail.com>, Ivan Sagalaev <maniac@softwaremaniacs.org>
*/

hljs.LANGUAGES.php = function() {
  var VARIABLE = {
    className: 'variable', begin: '\\$+[a-zA-Z_\x7f-\xff][a-zA-Z0-9_\x7f-\xff]*'
  };
  var STRINGS = [
    hljs.inherit(hljs.APOS_STRING_MODE, {illegal: null}),
    hljs.inherit(hljs.QUOTE_STRING_MODE, {illegal: null}),
    {
      className: 'string',
      begin: 'b"', end: '"',
      contains: [hljs.BACKSLASH_ESCAPE]
    },
    {
      className: 'string',
      begin: 'b\'', end: '\'',
      contains: [hljs.BACKSLASH_ESCAPE]
    }
  ];
  var NUMBERS = [
    hljs.C_NUMBER_MODE, // 0x..., 0..., decimal, float
    hljs.BINARY_NUMBER_MODE // 0b...
  ];
  var TITLE = {
    className: 'title', begin: hljs.UNDERSCORE_IDENT_RE
  };
  return {
    case_insensitive: true,
    defaultMode: {
      keywords:
        'and include_once list abstract global private echo interface as static endswitch ' +
        'array null if endwhile or const for endforeach self var while isset public ' +
        'protected exit foreach throw elseif include __FILE__ empty require_once do xor ' +
        'return implements parent clone use __CLASS__ __LINE__ else break print eval new ' +
        'catch __METHOD__ case exception php_user_filter default die require __FUNCTION__ ' +
        'enddeclare final try this switch continue endfor endif declare unset true false ' +
        'namespace trait goto instanceof insteadof __DIR__ __NAMESPACE__ __halt_compiler',
      contains: [
        hljs.C_LINE_COMMENT_MODE,
        hljs.HASH_COMMENT_MODE,
        {
          className: 'comment',
          begin: '/\\*', end: '\\*/',
          contains: [{
              className: 'phpdoc',
              begin: '\\s@[A-Za-z]+'
          }]
        },
        {
            className: 'comment',
            excludeBegin: true,
            begin: '__halt_compiler.+?;', endsWithParent: true
        },
        {
          className: 'string',
          begin: '<<<[\'"]?\\w+[\'"]?$', end: '^\\w+;',
          contains: [hljs.BACKSLASH_ESCAPE]
        },
        {
          className: 'preprocessor',
          begin: '<\\?php',
          relevance: 10
        },
        {
          className: 'preprocessor',
          begin: '\\?>'
        },
        VARIABLE,
        {
          className: 'function',
          beginWithKeyword: true, end: '{',
          keywords: 'function',
          illegal: '\\$',
          contains: [
            TITLE,
            {
              className: 'params',
              begin: '\\(', end: '\\)',
              contains: [
                'self',
                VARIABLE,
                hljs.C_BLOCK_COMMENT_MODE
              ].concat(STRINGS).concat(NUMBERS)
            }
          ]
        },
        {
          className: 'class',
          beginWithKeyword: true, end: '{',
          keywords: 'class',
          illegal: '[:\\(\\$]',
          contains: [
            {
              beginWithKeyword: true, endsWithParent: true,
              keywords: 'extends',
              contains: [TITLE]
            },
            TITLE
          ]
        },
        {
          begin: '=>' // No markup, just a relevance booster
        }
      ].concat(STRINGS).concat(NUMBERS)
    }
  };
}();

/*
Language: Python
*/

hljs.LANGUAGES.python = function() {
  var STRINGS = [
    {
      className: 'string',
      begin: '(u|b)?r?\'\'\'', end: '\'\'\'',
      relevance: 10
    },
    {
      className: 'string',
      begin: '(u|b)?r?"""', end: '"""',
      relevance: 10
    },
    {
      className: 'string',
      begin: '(u|r|ur)\'', end: '\'',
      contains: [hljs.BACKSLASH_ESCAPE],
      relevance: 10
    },
    {
      className: 'string',
      begin: '(u|r|ur)"', end: '"',
      contains: [hljs.BACKSLASH_ESCAPE],
      relevance: 10
    },
    {
      className: 'string',
      begin: '(b|br)\'', end: '\'',
      contains: [hljs.BACKSLASH_ESCAPE]
    },
    {
      className: 'string',
      begin: '(b|br)"', end: '"',
      contains: [hljs.BACKSLASH_ESCAPE]
    }
  ].concat([
    hljs.APOS_STRING_MODE,
    hljs.QUOTE_STRING_MODE
  ]);
  var TITLE = {
    className: 'title', begin: hljs.UNDERSCORE_IDENT_RE
  };
  var PARAMS = {
    className: 'params',
    begin: '\\(', end: '\\)',
    contains: ['self', hljs.C_NUMBER_MODE].concat(STRINGS)
  };
  var FUNC_CLASS_PROTO = {
    beginWithKeyword: true, end: ':',
    illegal: '[${]',
    contains: [TITLE, PARAMS],
    relevance: 10
  };

  return {
    defaultMode: {
      keywords: {
        keyword:
          'and elif is global as in if from raise for except finally print import pass return ' +
          'exec else break not with class assert yield try while continue del or def lambda ' +
          'nonlocal|10',
        built_in:
          'None True False Ellipsis NotImplemented'
      },
      illegal: '(</|->|\\?)',
      contains: STRINGS.concat([
        hljs.HASH_COMMENT_MODE,
        hljs.inherit(FUNC_CLASS_PROTO, {className: 'function', keywords: 'def'}),
        hljs.inherit(FUNC_CLASS_PROTO, {className: 'class', keywords: 'class'}),
        hljs.C_NUMBER_MODE,
        {
          className: 'decorator',
          begin: '@', end: '$'
        }
      ])
    }
  };
}();

/*
Language: Ruby
Author: Anton Kovalyov <anton@kovalyov.net>
Contributors: Peter Leonov <gojpeg@yandex.ru>, Vasily Polovnyov <vast@whiteants.net>, Loren Segal <lsegal@soen.ca>
*/

hljs.LANGUAGES.ruby = function(){
  var RUBY_IDENT_RE = '[a-zA-Z_][a-zA-Z0-9_]*(\\!|\\?)?';
  var RUBY_METHOD_RE = '[a-zA-Z_]\\w*[!?=]?|[-+~]\\@|<<|>>|=~|===?|<=>|[<>]=?|\\*\\*|[-/+%^&*~`|]|\\[\\]=?';
  var RUBY_KEYWORDS =
    'and false then defined module in return redo if BEGIN retry end for true self when ' +
    'next until do begin unless END rescue nil else break undef not super class case ' +
    'require yield alias while ensure elsif or def';
  var YARDOCTAG = {
    className: 'yardoctag',
    begin: '@[A-Za-z]+'
  };
  var COMMENTS = [
    {
      className: 'comment',
      begin: '#', end: '$',
      contains: [YARDOCTAG]
    },
    {
      className: 'comment',
      begin: '^\\=begin', end: '^\\=end',
      contains: [YARDOCTAG],
      relevance: 10
    },
    {
      className: 'comment',
      begin: '^__END__', end: '\\n$'
    }
  ];
  var SUBST = {
    className: 'subst',
    begin: '#\\{', end: '}',
    lexems: RUBY_IDENT_RE,
    keywords: RUBY_KEYWORDS
  };
  var STR_CONTAINS = [hljs.BACKSLASH_ESCAPE, SUBST];
  var STRINGS = [
    {
      className: 'string',
      begin: '\'', end: '\'',
      contains: STR_CONTAINS,
      relevance: 0
    },
    {
      className: 'string',
      begin: '"', end: '"',
      contains: STR_CONTAINS,
      relevance: 0
    },
    {
      className: 'string',
      begin: '%[qw]?\\(', end: '\\)',
      contains: STR_CONTAINS,
      relevance: 10
    },
    {
      className: 'string',
      begin: '%[qw]?\\[', end: '\\]',
      contains: STR_CONTAINS,
      relevance: 10
    },
    {
      className: 'string',
      begin: '%[qw]?{', end: '}',
      contains: STR_CONTAINS,
      relevance: 10
    },
    {
      className: 'string',
      begin: '%[qw]?<', end: '>',
      contains: STR_CONTAINS,
      relevance: 10
    },
    {
      className: 'string',
      begin: '%[qw]?/', end: '/',
      contains: STR_CONTAINS,
      relevance: 10
    },
    {
      className: 'string',
      begin: '%[qw]?%', end: '%',
      contains: STR_CONTAINS,
      relevance: 10
    },
    {
      className: 'string',
      begin: '%[qw]?-', end: '-',
      contains: STR_CONTAINS,
      relevance: 10
    },
    {
      className: 'string',
      begin: '%[qw]?\\|', end: '\\|',
      contains: STR_CONTAINS,
      relevance: 10
    }
  ];
  var FUNCTION = {
    className: 'function',
    begin: '\\bdef\\s+', end: ' |$|;',
    lexems: RUBY_IDENT_RE,
    keywords: RUBY_KEYWORDS,
    contains: [
      {
        className: 'title',
        begin: RUBY_METHOD_RE,
        lexems: RUBY_IDENT_RE,
        keywords: RUBY_KEYWORDS
      },
      {
        className: 'params',
        begin: '\\(', end: '\\)',
        lexems: RUBY_IDENT_RE,
        keywords: RUBY_KEYWORDS
      }
    ].concat(COMMENTS)
  };
  var IDENTIFIER = {
    className: 'identifier',
    begin: RUBY_IDENT_RE,
    lexems: RUBY_IDENT_RE,
    keywords: RUBY_KEYWORDS,
    relevance: 0
  };

  var RUBY_DEFAULT_CONTAINS = COMMENTS.concat(STRINGS.concat([
    {
      className: 'class',
      beginWithKeyword: true, end: '$|;',
      keywords: 'class module',
      contains: [
        {
          className: 'title',
          begin: '[A-Za-z_]\\w*(::\\w+)*(\\?|\\!)?',
          relevance: 0
        },
        {
          className: 'inheritance',
          begin: '<\\s*',
          contains: [{
            className: 'parent',
            begin: '(' + hljs.IDENT_RE + '::)?' + hljs.IDENT_RE
          }]
        }
      ].concat(COMMENTS)
    },
    FUNCTION,
    {
      className: 'constant',
      begin: '(::)?([A-Z]\\w*(::)?)+',
      relevance: 0
    },
    {
      className: 'symbol',
      begin: ':',
      contains: STRINGS.concat([IDENTIFIER]),
      relevance: 0
    },
    {
      className: 'number',
      begin: '(\\b0[0-7_]+)|(\\b0x[0-9a-fA-F_]+)|(\\b[1-9][0-9_]*(\\.[0-9_]+)?)|[0_]\\b',
      relevance: 0
    },
    {
      className: 'number',
      begin: '\\?\\w'
    },
    {
      className: 'variable',
      begin: '(\\$\\W)|((\\$|\\@\\@?)(\\w+))'
    },
    IDENTIFIER,
    { // regexp container
      begin: '(' + hljs.RE_STARTERS_RE + ')\\s*',
      contains: COMMENTS.concat([
        {
          className: 'regexp',
          begin: '/', end: '/[a-z]*',
          illegal: '\\n',
          contains: [hljs.BACKSLASH_ESCAPE]
        }
      ]),
      relevance: 0
    }
  ]));
  SUBST.contains = RUBY_DEFAULT_CONTAINS;
  FUNCTION.contains[1].contains = RUBY_DEFAULT_CONTAINS;

  return {
    defaultMode: {
      lexems: RUBY_IDENT_RE,
      keywords: RUBY_KEYWORDS,
      contains: RUBY_DEFAULT_CONTAINS
    }
  };
}();

/*
Language: Scala
Author: Jan Berkel <jan.berkel@gmail.com>
*/

hljs.LANGUAGES.scala = function() {
  var ANNOTATION = {
    className: 'annotation', begin: '@[A-Za-z]+'
  };
  var STRING = {
    className: 'string',
    begin: 'u?r?"""', end: '"""',
    relevance: 10
  };
  return {
    defaultMode: {
      keywords:
        'type yield lazy override def with val var false true sealed abstract private trait ' +
        'object null if for while throw finally protected extends import final return else ' +
        'break new catch super class case package default try this match continue throws',
      contains: [
        {
          className: 'javadoc',
          begin: '/\\*\\*', end: '\\*/',
          contains: [{
            className: 'javadoctag',
            begin: '@[A-Za-z]+'
          }],
          relevance: 10
        },
        hljs.C_LINE_COMMENT_MODE, hljs.C_BLOCK_COMMENT_MODE,
        hljs.APOS_STRING_MODE, hljs.QUOTE_STRING_MODE, STRING,
        {
          className: 'class',
          begin: '((case )?class |object |trait )', end: '({|$)', // beginWithKeyword won't work because a single "case" shouldn't start this mode
          illegal: ':',
          keywords: 'case class trait object',
          contains: [
            {
              beginWithKeyword: true,
              keywords: 'extends with',
              relevance: 10
            },
            {
              className: 'title',
              begin: hljs.UNDERSCORE_IDENT_RE
            },
            {
              className: 'params',
              begin: '\\(', end: '\\)',
              contains: [
                hljs.APOS_STRING_MODE, hljs.QUOTE_STRING_MODE, STRING,
                ANNOTATION
              ]
            }
          ]
        },
        hljs.C_NUMBER_MODE,
        ANNOTATION
      ]
    }
  };
}();

/*
Language: Smalltalk
Author: Vladimir Gubarkov <xonixx@gmail.com>
*/

hljs.LANGUAGES.smalltalk = function() {
  var VAR_IDENT_RE = '[a-z][a-zA-Z0-9_]*';
  var CHAR = {
    className: 'char',
    begin: '\\$.{1}'
  };
  var SYMBOL = {
    className: 'symbol',
    begin: '#' + hljs.UNDERSCORE_IDENT_RE
  };
  return {
    defaultMode: {
      keywords: 'self super nil true false thisContext', // only 6
      contains: [
        {
          className: 'comment',
          begin: '"', end: '"',
          relevance: 0
        },
        hljs.APOS_STRING_MODE,
        {
          className: 'class',
          begin: '\\b[A-Z][A-Za-z0-9_]*',
          relevance: 0
        },
        {
          className: 'method',
          begin: VAR_IDENT_RE + ':'
        },
        hljs.C_NUMBER_MODE,
        SYMBOL,
        CHAR,
        {
          className: 'localvars',
          begin: '\\|\\s*((' + VAR_IDENT_RE + ')\\s*)+\\|'
        },
        {
          className: 'array',
          begin: '\\#\\(', end: '\\)',
          contains: [
            hljs.APOS_STRING_MODE,
            CHAR,
            hljs.C_NUMBER_MODE,
            SYMBOL
          ]
        }
      ]
    }
  };
}();

/*
Language: SQL
*/

hljs.LANGUAGES.sql = {
  case_insensitive: true,
  defaultMode: {
    illegal: '[^\\s]',
    contains: [
      {
        className: 'operator',
        begin: '(begin|start|commit|rollback|savepoint|lock|alter|create|drop|rename|call|delete|do|handler|insert|load|replace|select|truncate|update|set|show|pragma|grant)\\b', end: ';', endsWithParent: true,
        keywords: {
          keyword: 'all partial global month current_timestamp using go revoke smallint ' +
            'indicator end-exec disconnect zone with character assertion to add current_user ' +
            'usage input local alter match collate real then rollback get read timestamp ' +
            'session_user not integer bit unique day minute desc insert execute like ilike|2 ' +
            'level decimal drop continue isolation found where constraints domain right ' +
            'national some module transaction relative second connect escape close system_user ' +
            'for deferred section cast current sqlstate allocate intersect deallocate numeric ' +
            'public preserve full goto initially asc no key output collation group by union ' +
            'session both last language constraint column of space foreign deferrable prior ' +
            'connection unknown action commit view or first into float year primary cascaded ' +
            'except restrict set references names table outer open select size are rows from ' +
            'prepare distinct leading create only next inner authorization schema ' +
            'corresponding option declare precision immediate else timezone_minute external ' +
            'varying translation true case exception join hour default double scroll value ' +
            'cursor descriptor values dec fetch procedure delete and false int is describe ' +
            'char as at in varchar null trailing any absolute current_time end grant ' +
            'privileges when cross check write current_date pad begin temporary exec time ' +
            'update catalog user sql date on identity timezone_hour natural whenever interval ' +
            'work order cascade diagnostics nchar having left call do handler load replace ' +
            'truncate start lock show pragma',
          aggregate: 'count sum min max avg'
        },
        contains: [
          {
            className: 'string',
            begin: '\'', end: '\'',
            contains: [hljs.BACKSLASH_ESCAPE, {begin: '\'\''}],
            relevance: 0
          },
          {
            className: 'string',
            begin: '"', end: '"',
            contains: [hljs.BACKSLASH_ESCAPE, {begin: '""'}],
            relevance: 0
          },
          {
            className: 'string',
            begin: '`', end: '`',
            contains: [hljs.BACKSLASH_ESCAPE]
          },
          hljs.C_NUMBER_MODE
        ]
      },
      hljs.C_BLOCK_COMMENT_MODE,
      {
        className: 'comment',
        begin: '--', end: '$'
      }
    ]
  }
};

/*
Language: VBScript
Author: Nikita Ledyaev <lenikita@yandex.ru>
Contributors: Michal Gabrukiewicz <mgabru@gmail.com>
*/

hljs.LANGUAGES.vbscript = {
  case_insensitive: true,
  defaultMode: {
    keywords: {
      keyword:
        'call class const dim do loop erase execute executeglobal exit for each next function ' +
        'if then else on error option explicit new private property let get public randomize ' +
        'redim rem select case set stop sub while wend with end to elseif is or xor and not ' +
        'class_initialize class_terminate default preserve in me byval byref step resume goto',
      built_in:
        'lcase month vartype instrrev ubound setlocale getobject rgb getref string ' +
        'weekdayname rnd dateadd monthname now day minute isarray cbool round formatcurrency ' +
        'conversions csng timevalue second year space abs clng timeserial fixs len asc ' +
        'isempty maths dateserial atn timer isobject filter weekday datevalue ccur isdate ' +
        'instr datediff formatdatetime replace isnull right sgn array snumeric log cdbl hex ' +
        'chr lbound msgbox ucase getlocale cos cdate cbyte rtrim join hour oct typename trim ' +
        'strcomp int createobject loadpicture tan formatnumber mid scriptenginebuildversion ' +
        'scriptengine split scriptengineminorversion cint sin datepart ltrim sqr ' +
        'scriptenginemajorversion time derived eval date formatpercent exp inputbox left ascw ' +
        'chrw regexp server response request cstr err',
      literal:
        'true false null nothing empty'
    },
    illegal: '//',
    contains: [
      { // can’t use standard QUOTE_STRING_MODE since it’s compiled with its own escape and doesn’t use the local one
        className: 'string',
        begin: '"', end: '"',
        illegal: '\\n',
        contains: [{begin: '""'}],
        relevance: 0
      },
      {
        className: 'comment',
        begin: '\'', end: '$'
      },
      hljs.C_NUMBER_MODE
    ]
  }
};

/*
Language: HTML, XML
*/

hljs.LANGUAGES.xml = function(){
  var XML_IDENT_RE = '[A-Za-z0-9\\._:-]+';
  var TAG_INTERNALS = {
    endsWithParent: true,
    contains: [
      {
        className: 'attribute',
        begin: XML_IDENT_RE,
        relevance: 0
      },
      {
        begin: '="', returnBegin: true, end: '"',
        contains: [{
            className: 'value',
            begin: '"', endsWithParent: true
        }]
      },
      {
        begin: '=\'', returnBegin: true, end: '\'',
        contains: [{
          className: 'value',
          begin: '\'', endsWithParent: true
        }]
      },
      {
        begin: '=',
        contains: [{
          className: 'value',
          begin: '[^\\s/>]+'
        }]
      }
    ]
  };
  return {
    case_insensitive: true,
    defaultMode: {
      contains: [
        {
          className: 'pi',
          begin: '<\\?', end: '\\?>',
          relevance: 10
        },
        {
          className: 'doctype',
          begin: '<!DOCTYPE', end: '>',
          relevance: 10,
          contains: [{begin: '\\[', end: '\\]'}]
        },
        {
          className: 'comment',
          begin: '<!--', end: '-->',
          relevance: 10
        },
        {
          className: 'cdata',
          begin: '<\\!\\[CDATA\\[', end: '\\]\\]>',
          relevance: 10
        },
        {
          className: 'tag',
          /*
          The lookahead pattern (?=...) ensures that 'begin' only matches
          '<style' as a single word, followed by a whitespace or an
          ending braket. The '$' is needed for the lexem to be recognized
          by hljs.subMode() that tests lexems outside the stream.
          */
          begin: '<style(?=\\s|>|$)', end: '>',
          keywords: {title: 'style'},
          contains: [TAG_INTERNALS],
          starts: {
            end: '</style>', returnEnd: true,
            subLanguage: 'css'
          }
        },
        {
          className: 'tag',
          // See the comment in the <style tag about the lookahead pattern
          begin: '<script(?=\\s|>|$)', end: '>',
          keywords: {title: 'script'},
          contains: [TAG_INTERNALS],
          starts: {
            end: '</script>', returnEnd: true,
            subLanguage: 'javascript'
          }
        },
        {
          begin: '<%', end: '%>',
          subLanguage: 'vbscript'
        },
        {
          className: 'tag',
          begin: '</?', end: '/?>',
          contains: [
            {
              className: 'title', begin: '[^ />]+'
            },
            TAG_INTERNALS
          ]
        }
      ]
    }
  };
}();
