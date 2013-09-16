
/*
Core highlighting function. Accepts a string with the code to highlight and 
optionaly a language name. Returns an object with the following properties:

- language (detected language)
- relevance (int)
- keyword_count (int)
- value (an HTML string with highlighting markup)
- second (object with the same structure for second-best heuristically
  detected language, may be absent)

*/

var hljs = function(value, language_name) {
  if(!language_name){
    var result = {
      keyword_count: 0,
      relevance: 0,
      value: hljs.escape(value)
    };
    var second = result;
    for (var key in hljs.LANGUAGES) {
      if (!hljs.LANGUAGES.hasOwnProperty(key))
        continue;
      var current = hljs(value, key);
      current.language = key;
      if (current.keyword_count + current.relevance > second.keyword_count + second.relevance) {
        second = current;
      }
      if (current.keyword_count + current.relevance > result.keyword_count + result.relevance) {
        second = result;
        result = current;
      }
    }
    if (second.language) {
      result.second = second;
    }
    return result;
  }

  function subMode(lexem, mode) {
    for (var i = 0; i < mode.contains.length; i++) {
      var match = mode.contains[i].beginRe.exec(lexem);
      if (match && match.index == 0) {
        return mode.contains[i];
      }
    }
  }

  function endOfMode(mode_index, lexem) {
    if (modes[mode_index].end && modes[mode_index].endRe.test(lexem))
      return 1;
    if (modes[mode_index].endsWithParent) {
      var level = endOfMode(mode_index - 1, lexem);
      return level ? level + 1 : 0;
    }
    return 0;
  }

  function isIllegal(lexem, mode) {
    return mode.illegal && mode.illegalRe.test(lexem);
  }

  function compileTerminators(mode, language) {
    var terminators = [];

    for (var i = 0; i < mode.contains.length; i++) {
      terminators.push(mode.contains[i].begin);
    }

    var index = modes.length - 1;
    do {
      if (modes[index].end) {
        terminators.push(modes[index].end);
      }
      index--;
    } while (modes[index + 1].endsWithParent);

    if (mode.illegal) {
      terminators.push(mode.illegal);
    }

    return terminators.length ? langRe(language, terminators.join('|'), true) : null;
  }

  function eatModeChunk(value, index) {
    var mode = modes[modes.length - 1];
    if (mode.terminators === undefined) {
      mode.terminators = compileTerminators(mode, language);
    }
    var match;
    if (mode.terminators) {
      mode.terminators.lastIndex = index;
      match = mode.terminators.exec(value);
    }
    return match ? [value.substr(index, match.index - index), match[0], false] : [value.substr(index), '', true];
  }

  function keywordMatch(mode, match) {
    var match_str = language.case_insensitive ? match[0].toLowerCase() : match[0];
    var value = mode.keywords[match_str];
    if (value && value instanceof Array)
      return value;
    return false;
  }

  function processKeywords(buffer, mode) {
    buffer = hljs.escape(buffer);
    if (!mode.keywords)
      return buffer;
    var result = '';
    var last_index = 0;
    mode.lexemsRe.lastIndex = 0;
    var match = mode.lexemsRe.exec(buffer);
    while (match) {
      result += buffer.substr(last_index, match.index - last_index);
      var keyword_match = keywordMatch(mode, match);
      if (keyword_match) {
        keyword_count += keyword_match[1];
        result += '<span class="'+ keyword_match[0] +'">' + match[0] + '</span>';
      } else {
        result += match[0];
      }
      last_index = mode.lexemsRe.lastIndex;
      match = mode.lexemsRe.exec(buffer);
    }
    return result + buffer.substr(last_index, buffer.length - last_index);
  }

  function processSubLanguage(buffer, mode) {
    var result;
    if (mode.subLanguage == '') {
      result = hljs(buffer);
    } else {
      result = hljs(buffer, mode.subLanguage);
    }
    // Counting embedded language score towards the host language may be disabled
    // with zeroing the containing mode relevance. Usecase in point is Markdown that
    // allows XML everywhere and makes every XML snippet to have a much larger Markdown
    // score.
    if (mode.relevance > 0) {
      keyword_count += result.keyword_count;
      relevance += result.relevance;
    }
    return '<span class="' + result.language  + '">' + result.value + '</span>';
  }

  function processBuffer(buffer, mode) {
    if (mode.subLanguage && hljs.LANGUAGES[mode.subLanguage] || mode.subLanguage == '') {
      return processSubLanguage(buffer, mode);
    } else {
      return processKeywords(buffer, mode);
    }
  }

  function startNewMode(mode, lexem) {
    var markup = mode.className?'<span class="' + mode.className + '">':'';
    if (mode.returnBegin) {
      result += markup;
      mode.buffer = '';
    } else if (mode.excludeBegin) {
      result += hljs.escape(lexem) + markup;
      mode.buffer = '';
    } else {
      result += markup;
      mode.buffer = lexem;
    }
    modes.push(mode);
    relevance += mode.relevance;
  }

  function processModeInfo(buffer, lexem, end) {
    var current_mode = modes[modes.length - 1];
    if (end) {
      result += processBuffer(current_mode.buffer + buffer, current_mode);
      return false;
    }

    var new_mode = subMode(lexem, current_mode);
    if (new_mode) {
      result += processBuffer(current_mode.buffer + buffer, current_mode);
      startNewMode(new_mode, lexem);
      return new_mode.returnBegin;
    }

    var end_level = endOfMode(modes.length - 1, lexem);
    if (end_level) {
      var markup = current_mode.className?'</span>':'';
      if (current_mode.returnEnd) {
        result += processBuffer(current_mode.buffer + buffer, current_mode) + markup;
      } else if (current_mode.excludeEnd) {
        result += processBuffer(current_mode.buffer + buffer, current_mode) + markup + hljs.escape(lexem);
      } else {
        result += processBuffer(current_mode.buffer + buffer + lexem, current_mode) + markup;
      }
      while (end_level > 1) {
        markup = modes[modes.length - 2].className?'</span>':'';
        result += markup;
        end_level--;
        modes.length--;
      }
      var last_ended_mode = modes[modes.length - 1];
      modes.length--;
      modes[modes.length - 1].buffer = '';
      if (last_ended_mode.starts) {
        startNewMode(last_ended_mode.starts, '');
      }
      return current_mode.returnEnd;
    }

    if (isIllegal(lexem, current_mode))
      throw 'Illegal';
  }
    
  function langRe(language, value, global) {
    return RegExp(
      value,
      'm' + (language.case_insensitive ? 'i' : '') + (global ? 'g' : '')
    );
  }

  function compileMode(mode, language, is_default) {
    if (mode.compiled)
      return;

    var keywords = []; // used later with beginWithKeyword but filled as a side-effect of keywords compilation
    if (mode.keywords) {
      var compiled_keywords = {};

      function flatten(className, str) {
        var group = str.split(' ');
        for (var i = 0; i < group.length; i++) {
          var pair = group[i].split('|');
          compiled_keywords[pair[0]] = [className, pair[1] ? Number(pair[1]) : 1];
          keywords.push(pair[0]);
        }
      }

      mode.lexemsRe = langRe(language, mode.lexems || hljs.IDENT_RE, true);
      if (typeof mode.keywords == 'string') { // string
        flatten('keyword', mode.keywords)
      } else {
        for (var className in mode.keywords) {
          if (!mode.keywords.hasOwnProperty(className))
            continue;
          flatten(className, mode.keywords[className]);
        }
      }
      mode.keywords = compiled_keywords;
    }
    if (!is_default) {
      if (mode.beginWithKeyword) {
        mode.begin = '\\b(' + keywords.join('|') + ')\\s';
      }
      mode.beginRe = langRe(language, mode.begin ? mode.begin : '\\B|\\b');
      if (!mode.end && !mode.endsWithParent)
        mode.end = '\\B|\\b';
      if (mode.end)
        mode.endRe = langRe(language, mode.end);
    }
    if (mode.illegal)
      mode.illegalRe = langRe(language, mode.illegal);
    if (mode.relevance === undefined)
      mode.relevance = 1;
    if (!mode.contains) {
      mode.contains = [];
    }
    // compiled flag is set before compiling submodes to avoid self-recursion
    // (see lisp where quoted_list contains quoted_list)
    mode.compiled = true;
    for (var i = 0; i < mode.contains.length; i++) {
      if (mode.contains[i] == 'self') {
        mode.contains[i] = mode;
      }
      compileMode(mode.contains[i], language, false);
    }
    if (mode.starts) {
      compileMode(mode.starts, language, false);
    }
  }

  var language = hljs.LANGUAGES[language_name];
  
  if(!language) {
    return {
      language: language_name,
      relevance: 0,
      keyword_count: 0,
      value: hljs.escape(value)
    };
  }

  var modes = [language.defaultMode];
  var relevance = 0;
  var keyword_count = 0;
  var result = '';
  compileMode(language.defaultMode, language, true);    
  try {
    var mode_info, index = 0;
    language.defaultMode.buffer = '';
    do {
      mode_info = eatModeChunk(value, index);
      var return_lexem = processModeInfo(mode_info[0], mode_info[1], mode_info[2]);
      index += mode_info[0].length;
      if (!return_lexem) {
        index += mode_info[1].length;
      }
    } while (!mode_info[2]);
    if(modes.length > 2 || (modes.length == 2 && !modes[1].endsWithParent))
      throw 'Illegal';
    return {
      language: language_name,
      relevance: relevance,
      keyword_count: keyword_count,
      value: result
    };
  } catch (e) {
    if (e == 'Illegal') {
      return {
        language: language_name,
        relevance: 0,
        keyword_count: 0,
        value: hljs.escape(value)
      };
    } else {
      throw e;
    }
  }
}

hljs.LANGUAGES = {};


// Common regexps
hljs.IDENT_RE = '[a-zA-Z][a-zA-Z0-9_]*';
hljs.UNDERSCORE_IDENT_RE = '[a-zA-Z_][a-zA-Z0-9_]*';
hljs.NUMBER_RE = '\\b\\d+(\\.\\d+)?';
hljs.C_NUMBER_RE = '\\b(0[xX][a-fA-F0-9]+|(\\d+(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?)'; // 0x..., 0..., decimal, float
hljs.BINARY_NUMBER_RE = '\\b(0b[01]+)'; // 0b...
hljs.RE_STARTERS_RE = '!|!=|!==|%|%=|&|&&|&=|\\*|\\*=|\\+|\\+=|,|\\.|-|-=|/|/=|:|;|<|<<|<<=|<=|=|==|===|>|>=|>>|>>=|>>>|>>>=|\\?|\\[|\\{|\\(|\\^|\\^=|\\||\\|=|\\|\\||~';
hljs.EOF_RE = '(?![\\s\\S])';

// Common modes
hljs.BACKSLASH_ESCAPE = {
  begin: '\\\\.', relevance: 0
};
hljs.APOS_STRING_MODE = {
  className: 'string',
  begin: '\'', end: '\'',
  illegal: '\\n',
  contains: [hljs.BACKSLASH_ESCAPE],
  relevance: 0
};
hljs.QUOTE_STRING_MODE = {
  className: 'string',
  begin: '"', end: '"',
  illegal: '\\n',
  contains: [hljs.BACKSLASH_ESCAPE],
  relevance: 0
};
hljs.C_LINE_COMMENT_MODE = {
  className: 'comment',
  begin: '//', end: '$'
};
hljs.C_BLOCK_COMMENT_MODE = {
  className: 'comment',
  begin: '/\\*', end: '\\*/'
};
hljs.HASH_COMMENT_MODE = {
  className: 'comment',
  begin: '#', end: '$'
};
hljs.NUMBER_MODE = {
  className: 'number',
  begin: hljs.NUMBER_RE,
  relevance: 0
};
hljs.C_NUMBER_MODE = {
  className: 'number',
  begin: hljs.C_NUMBER_RE,
  relevance: 0
};
hljs.BINARY_NUMBER_MODE = {
  className: 'number',
  begin: hljs.BINARY_NUMBER_RE,
  relevance: 0
};

// Utility functions
hljs.escape = function(value) {
  return value.replace(/&/gm, '&amp;').replace(/</gm, '&lt;');
}

hljs.inherit = function(parent, obj) {
  var result = {}
  for (var key in parent)
    result[key] = parent[key];
  if (obj)
    for (var key in obj)
       result[key] = obj[key];
  return result;
}