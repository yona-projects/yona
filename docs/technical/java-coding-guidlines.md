Java Coding Guidelines
======================

This document describes the guildeline you should follow when write code for
Yobi in Java.

In this document, The words "MUST", "MUST NOT", "SHOULD", "SHOULD NOT" and
"MAY" to be interpreted as follows:

* MUST, MUST NOT - This word mean that you must follow the definition and we
  will try to fix the legacy code does not follow the definition.
* SHOULD, SHOULD NOT - This word mean that you must follow the definition and
  we don't need to fix all legacy code to follow the definition.
* MAY - This word mean that you may follow the definition and must not do
  anything to make a problem if someone follows the definition.

Use @Nullable and @Nonnull
---------------------------

### @Nullable

@Nullable is an annotation defined by javax.annotation.Nullable. If an element
is annotated with @Nullable, we say the element is nullable and it means the
element may have NULL value. Similarly, if a method is annotated with
@Nullable, we say the method is nullable and it means the method may return
NULL.

In a public method:

* You SHOULD annotate the method with @Nullable if it may return NULL value.
* For every parameter of the method, you SHOULD annotate the parameter with
  @Nullable if it may have NULL value.

You MUST NOT write code which clearly has a chance to throw
a NullPointerException if the nullable variable is null.

### @Nonnull

@Nonnull is an annotation defined by javax.annotation.Nonnull. If a variable is
annotated with @Nonnull, we say the variable is nonnull and it means the
variable never have NULL value. Similarly, if a method is annotated with
@Nonnull, we say the method is nonnull and it means the method never return
NULL.

In a public method:

* You SHOULD annotate the method with @Nonnull if you can make sure it never
  return NULL value.
* For every parameter of the method, you SHOULD annotate the parameter with
  @Nonnull if it is required not to have NULL value.

You MUST NOT set a nonnull field to NULL, MUST NOT pass a NULL argument via
a nonnull parameter and MUST NOT make a nonnull method returns NULL.

You MAY suppose that nonnull fields and parameters never have NULL value and
nonnull methods never return NULL.
