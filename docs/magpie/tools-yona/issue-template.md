<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Yona — issue-body field schema

The skills treat every tracker's **issue body** as a structured document with named fields: a markdown `### <field name>` heading followed by a value block. The set of field names is the schema the skills read from and write to. The convention is plain text, so it works on a Yona issue exactly as on a GitHub issue; only the way the body is read and written differs.

The field names are project-specific. Generic skill logic refers to them by **role** (*the CVE-tool-link field*, *the public-advisory URL field*), and the role to concrete-name mapping is declared in the project manifest.

## Where the schema is authoritative

Yona has no issue-form file (the GitHub `ISSUE_TEMPLATE` YAML). Two surfaces must stay in lock-step whenever a field is added, renamed or removed:

1. **The project manifest**: `<project-config>/project.md` declares the concrete field name each skill role maps to.
2. **The skills that write fresh issue bodies**: they render the full field set when they create an issue with `issue_create` in [`operations.md`](operations.md#create).

Because nothing in Yona enforces the schema, an issue filed by hand through the web UI may lack the headings. The skills treat a missing heading as an empty field and add it when they write the field.

## Empty-field convention

A field is **empty** when its value block is blank or consists of the single line `_No response_` (the GitHub form placeholder, kept so that trackers moved from GitHub keep working). Writing an empty value writes `_No response_`.

## Body-field surgery

Reading one field and replacing one field never touches the rest of the body. `<field>` is the heading text without the `### `. The value goes through a file, never through the command line:

<!-- recipe:body_field_get -->
```bash
yona issue view <N> -R <upstream> --json body | jq -r .body \
  | awk -v field='<field>' '$0 == "### " field { on = 1; next } /^### / { on = 0 } on' \
  | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' | sed '/./,$!d'
```

`body_field_set` replaces the value block of `<field>` with the contents of `<value-file>`, or appends the heading and the value at the end of the body when the heading does not exist. It prints the number of the issue it wrote:

<!-- recipe:body_field_set -->
```bash
tmp=$(mktemp); yona issue view <N> -R <upstream> --json body | jq -r .body > "$tmp"
awk -v field='<field>' -v valuefile='<value-file>' '
  function emit(   line) { print "### " field; print ""; while ((getline line < valuefile) > 0) print line; print "" }
  $0 == "### " field { emit(); skip = 1; found = 1; next }
  /^### / { skip = 0 }
  !skip { print }
  END { if (!found) { if (NR > 0) print ""; emit() } }' "$tmp" > "$tmp.new"
jq -n --rawfile body "$tmp.new" '{body: $body}' \
  | yona api -X PATCH /-_-api/v1/owners/<owner>/projects/<project>/issues/<N> --input - | jq -r '.result.number'
rm -f "$tmp" "$tmp.new"
```
