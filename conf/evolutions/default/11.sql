# --- !Ups

ALTER TABLE tag ADD COLUMN category VARCHAR(255) NOT NULL;
UPDATE tag SET category='Tag';
ALTER TABLE tag DROP CONSTRAINT uq_tag_name;
ALTER TABLE tag ADD CONSTRAINT uq_tag_category_name UNIQUE (category, name);

INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'ALGOL 58');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'ALGOL 60');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'ALGOL 68');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'APL');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'ASP.NET');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'AWK');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'ActionScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Ada');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'AppleScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'AspectJ');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Assembly language');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'AutoLISP / Visual LISP');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'B');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'BASIC');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'BCPL');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'BREW');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Bash');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Batch (Windows/Dos)');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Bourne shell');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'C#');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'C');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'C++');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'COBOL');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Clipper');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Clojure');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'CobolScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'CoffeeScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'ColdFusion');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Common Lisp');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Component Pascal');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Curl');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'D');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Dart');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Delphi');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'ECMAScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Eiffel');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Emacs Lisp');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Erlang');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'F#');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'F');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Forth');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Fortran');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'FoxBase');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'FoxPro');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Go!');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Go');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Groovy');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Haskell');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Io');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'J');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'JScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Java');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'JavaFX Script');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'JavaScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'LaTeX');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Lisp');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Logo');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Lua');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'MATLAB');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'MDL');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'ML');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Machine code');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Mathematica');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Maya');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Microcode');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Microsoft Visual C++');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Modula');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Modula-2');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Modula-3');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'OCaml');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Object Lisp');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Object Pascal');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Objective-C');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Opa');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Orc');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PHP');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PL-11');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PL/0');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PL/B');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PL/C');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PL/I');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PL/M');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PL/P');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PL/SQL');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'POP-11');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Pascal');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Perl');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PostScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PowerBuilder');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'PowerShell');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Processing.js');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Prolog');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Python');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'QBasic');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'QuakeC');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'R');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'R++');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'REXX');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Ruby');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Rust');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Scala');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Scheme');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Script.NET');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Sed');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Self');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Simula');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Simulink');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Small Basic');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Smalltalk');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Snowball');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Squeak');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'TEX');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Tcl');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'TeX');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'UNITY');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Unix shell');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'UnrealScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'VBA');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'VBScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'VHDL');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Vala');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Verilog');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual Basic .NET');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual Basic');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual C#');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual DataFlex');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual DialogScript');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual Fortran');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual FoxPro');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual J#');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual J++');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual LISP');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Visual Prolog');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'Windows PowerShell');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'XQuery');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'XSLT');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'bc');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'csh');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'dBase');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'ksh');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'Language ', 'make');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'Apache');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'BSD');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'EPL');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'GPL');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'ISC');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'LGPL');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'MIT');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'MPL v1.1');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'MPL v2.0');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'License', 'Public Domain');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'OS', 'Linux');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'OS', 'OSX');
INSERT INTO tag (id, category, name) VALUES (nextval('tag_seq'), 'OS', 'Windows');

# --- !Downs

ALTER TABLE tag DROP COLUMN category;
ALTER TABLE tag ADD CONSTRAINT uq_tag_name UNIQUE (name);
ALTER TABLE tag DROP CONSTRAINT uq_tag_category_name;

DELETE FROM tag WHERE category='Language' AND name='ALGOL 58'
DELETE FROM tag WHERE category='Language' AND name='ALGOL 60'
DELETE FROM tag WHERE category='Language' AND name='ALGOL 68'
DELETE FROM tag WHERE category='Language' AND name='APL'
DELETE FROM tag WHERE category='Language' AND name='ASP.NET'
DELETE FROM tag WHERE category='Language' AND name='AWK'
DELETE FROM tag WHERE category='Language' AND name='ActionScript'
DELETE FROM tag WHERE category='Language' AND name='Ada'
DELETE FROM tag WHERE category='Language' AND name='AppleScript'
DELETE FROM tag WHERE category='Language' AND name='AspectJ'
DELETE FROM tag WHERE category='Language' AND name='Assembly language'
DELETE FROM tag WHERE category='Language' AND name='AutoLISP / Visual LISP'
DELETE FROM tag WHERE category='Language' AND name='B'
DELETE FROM tag WHERE category='Language' AND name='BASIC'
DELETE FROM tag WHERE category='Language' AND name='BCPL'
DELETE FROM tag WHERE category='Language' AND name='BREW'
DELETE FROM tag WHERE category='Language' AND name='Bash'
DELETE FROM tag WHERE category='Language' AND name='Batch (Windows/Dos)'
DELETE FROM tag WHERE category='Language' AND name='Bourne shell'
DELETE FROM tag WHERE category='Language' AND name='C#'
DELETE FROM tag WHERE category='Language' AND name='C'
DELETE FROM tag WHERE category='Language' AND name='C++'
DELETE FROM tag WHERE category='Language' AND name='COBOL'
DELETE FROM tag WHERE category='Language' AND name='Clipper'
DELETE FROM tag WHERE category='Language' AND name='Clojure'
DELETE FROM tag WHERE category='Language' AND name='CobolScript'
DELETE FROM tag WHERE category='Language' AND name='CoffeeScript'
DELETE FROM tag WHERE category='Language' AND name='ColdFusion'
DELETE FROM tag WHERE category='Language' AND name='Common Lisp'
DELETE FROM tag WHERE category='Language' AND name='Component Pascal'
DELETE FROM tag WHERE category='Language' AND name='Curl'
DELETE FROM tag WHERE category='Language' AND name='D'
DELETE FROM tag WHERE category='Language' AND name='Dart'
DELETE FROM tag WHERE category='Language' AND name='Delphi'
DELETE FROM tag WHERE category='Language' AND name='ECMAScript'
DELETE FROM tag WHERE category='Language' AND name='Eiffel'
DELETE FROM tag WHERE category='Language' AND name='Emacs Lisp'
DELETE FROM tag WHERE category='Language' AND name='Erlang'
DELETE FROM tag WHERE category='Language' AND name='F#'
DELETE FROM tag WHERE category='Language' AND name='F'
DELETE FROM tag WHERE category='Language' AND name='Forth'
DELETE FROM tag WHERE category='Language' AND name='Fortran'
DELETE FROM tag WHERE category='Language' AND name='FoxBase'
DELETE FROM tag WHERE category='Language' AND name='FoxPro'
DELETE FROM tag WHERE category='Language' AND name='Go!'
DELETE FROM tag WHERE category='Language' AND name='Go'
DELETE FROM tag WHERE category='Language' AND name='Groovy'
DELETE FROM tag WHERE category='Language' AND name='Haskell'
DELETE FROM tag WHERE category='Language' AND name='Io'
DELETE FROM tag WHERE category='Language' AND name='J'
DELETE FROM tag WHERE category='Language' AND name='JScript'
DELETE FROM tag WHERE category='Language' AND name='Java'
DELETE FROM tag WHERE category='Language' AND name='JavaFX Script'
DELETE FROM tag WHERE category='Language' AND name='JavaScript'
DELETE FROM tag WHERE category='Language' AND name='LaTeX'
DELETE FROM tag WHERE category='Language' AND name='Lisp'
DELETE FROM tag WHERE category='Language' AND name='Logo'
DELETE FROM tag WHERE category='Language' AND name='Lua'
DELETE FROM tag WHERE category='Language' AND name='MATLAB'
DELETE FROM tag WHERE category='Language' AND name='MDL'
DELETE FROM tag WHERE category='Language' AND name='ML'
DELETE FROM tag WHERE category='Language' AND name='Machine code'
DELETE FROM tag WHERE category='Language' AND name='Mathematica'
DELETE FROM tag WHERE category='Language' AND name='Maya'
DELETE FROM tag WHERE category='Language' AND name='Microcode'
DELETE FROM tag WHERE category='Language' AND name='Microsoft Visual C++'
DELETE FROM tag WHERE category='Language' AND name='Modula'
DELETE FROM tag WHERE category='Language' AND name='Modula-2'
DELETE FROM tag WHERE category='Language' AND name='Modula-3'
DELETE FROM tag WHERE category='Language' AND name='OCaml'
DELETE FROM tag WHERE category='Language' AND name='Object Lisp'
DELETE FROM tag WHERE category='Language' AND name='Object Pascal'
DELETE FROM tag WHERE category='Language' AND name='Objective-C'
DELETE FROM tag WHERE category='Language' AND name='Opa'
DELETE FROM tag WHERE category='Language' AND name='Orc'
DELETE FROM tag WHERE category='Language' AND name='PHP'
DELETE FROM tag WHERE category='Language' AND name='PL-11'
DELETE FROM tag WHERE category='Language' AND name='PL/0'
DELETE FROM tag WHERE category='Language' AND name='PL/B'
DELETE FROM tag WHERE category='Language' AND name='PL/C'
DELETE FROM tag WHERE category='Language' AND name='PL/I'
DELETE FROM tag WHERE category='Language' AND name='PL/M'
DELETE FROM tag WHERE category='Language' AND name='PL/P'
DELETE FROM tag WHERE category='Language' AND name='PL/SQL'
DELETE FROM tag WHERE category='Language' AND name='POP-11'
DELETE FROM tag WHERE category='Language' AND name='Pascal'
DELETE FROM tag WHERE category='Language' AND name='Perl'
DELETE FROM tag WHERE category='Language' AND name='PostScript'
DELETE FROM tag WHERE category='Language' AND name='PowerBuilder'
DELETE FROM tag WHERE category='Language' AND name='PowerShell'
DELETE FROM tag WHERE category='Language' AND name='Processing.js'
DELETE FROM tag WHERE category='Language' AND name='Prolog'
DELETE FROM tag WHERE category='Language' AND name='Python'
DELETE FROM tag WHERE category='Language' AND name='QBasic'
DELETE FROM tag WHERE category='Language' AND name='QuakeC'
DELETE FROM tag WHERE category='Language' AND name='R'
DELETE FROM tag WHERE category='Language' AND name='R++'
DELETE FROM tag WHERE category='Language' AND name='REXX'
DELETE FROM tag WHERE category='Language' AND name='Ruby'
DELETE FROM tag WHERE category='Language' AND name='Rust'
DELETE FROM tag WHERE category='Language' AND name='Scala'
DELETE FROM tag WHERE category='Language' AND name='Scheme'
DELETE FROM tag WHERE category='Language' AND name='Script.NET'
DELETE FROM tag WHERE category='Language' AND name='Sed'
DELETE FROM tag WHERE category='Language' AND name='Self'
DELETE FROM tag WHERE category='Language' AND name='Simula'
DELETE FROM tag WHERE category='Language' AND name='Simulink'
DELETE FROM tag WHERE category='Language' AND name='Small Basic'
DELETE FROM tag WHERE category='Language' AND name='Smalltalk'
DELETE FROM tag WHERE category='Language' AND name='Snowball'
DELETE FROM tag WHERE category='Language' AND name='Squeak'
DELETE FROM tag WHERE category='Language' AND name='TEX'
DELETE FROM tag WHERE category='Language' AND name='Tcl'
DELETE FROM tag WHERE category='Language' AND name='TeX'
DELETE FROM tag WHERE category='Language' AND name='UNITY'
DELETE FROM tag WHERE category='Language' AND name='Unix shell'
DELETE FROM tag WHERE category='Language' AND name='UnrealScript'
DELETE FROM tag WHERE category='Language' AND name='VBA'
DELETE FROM tag WHERE category='Language' AND name='VBScript'
DELETE FROM tag WHERE category='Language' AND name='VHDL'
DELETE FROM tag WHERE category='Language' AND name='Vala'
DELETE FROM tag WHERE category='Language' AND name='Verilog'
DELETE FROM tag WHERE category='Language' AND name='Visual Basic .NET'
DELETE FROM tag WHERE category='Language' AND name='Visual Basic'
DELETE FROM tag WHERE category='Language' AND name='Visual C#'
DELETE FROM tag WHERE category='Language' AND name='Visual DataFlex'
DELETE FROM tag WHERE category='Language' AND name='Visual DialogScript'
DELETE FROM tag WHERE category='Language' AND name='Visual Fortran'
DELETE FROM tag WHERE category='Language' AND name='Visual FoxPro'
DELETE FROM tag WHERE category='Language' AND name='Visual J#'
DELETE FROM tag WHERE category='Language' AND name='Visual J++'
DELETE FROM tag WHERE category='Language' AND name='Visual LISP'
DELETE FROM tag WHERE category='Language' AND name='Visual Prolog'
DELETE FROM tag WHERE category='Language' AND name='Windows PowerShell'
DELETE FROM tag WHERE category='Language' AND name='XQuery'
DELETE FROM tag WHERE category='Language' AND name='XSLT'
DELETE FROM tag WHERE category='Language' AND name='bc'
DELETE FROM tag WHERE category='Language' AND name='csh'
DELETE FROM tag WHERE category='Language' AND name='dBase'
DELETE FROM tag WHERE category='Language' AND name='ksh'
DELETE FROM tag WHERE category='Language' AND name='make'
DELETE FROM tag WHERE category='License' AND name='Apache'
DELETE FROM tag WHERE category='License' AND name='BSD'
DELETE FROM tag WHERE category='License' AND name='EPL'
DELETE FROM tag WHERE category='License' AND name='GPL'
DELETE FROM tag WHERE category='License' AND name='ISC'
DELETE FROM tag WHERE category='License' AND name='LGPL'
DELETE FROM tag WHERE category='License' AND name='MIT'
DELETE FROM tag WHERE category='License' AND name='MPL v1.1'
DELETE FROM tag WHERE category='License' AND name='MPL v2.0'
DELETE FROM tag WHERE category='License' AND name='Public Domain'
DELETE FROM tag WHERE category='OS' AND name='Linux'
DELETE FROM tag WHERE category='OS' AND name='OSX'
DELETE FROM tag WHERE category='OS' AND name='Windows'
