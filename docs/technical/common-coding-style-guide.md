Common Coding Style Guide
=========================

This document defines the coding style guide for Yobi project.

Follow this guide when you write a source code regardless of language. But if
there is a style guide, a rule or a convention for a specific language and for
Yobi project, follow it for source code in the language.

Style For Source Code
---------------------

* Use UTF-8.
* Use space(ASCII 32), not tab(ASCII 9), for indentation.
* Use LF(ASCII 10), not CR nor CRLF, as a newline character.
* Every source file must end with a newline character.

Style For Commit Message
------------------------

* Write summary of the commit in the first line. (50 characters is soft limit)
* Write detailed description from the third line. (Skip the second line)

This style comes from the SubmittingPatches document of Git project. [1]

Advice For Introducing New Style Guide
--------------------------------------

It is very common to import a snippet of source code from other projects. To
avoid hassle to fix coding style of them, follow style guides commonly used
among open source projects if possible.

References
----------

[1]: http://git.kernel.org/?p=git/git.git;a=blob;f=Documentation/SubmittingPatches;hb=HEAD
