; Tree-sitter highlight queries for .melt files.
;
; .melt files are parsed with the HTML tree-sitter grammar as the base.
; Scala and CSS sections are highlighted via injection (see injections.scm).
; This file adds Melt-specific highlights on top of the HTML defaults.

; Highlight the <script lang="scala"> opening/closing tags
((script_element
  (start_tag
    (tag_name) @tag
    (attribute
      (attribute_name) @attribute.name
      (quoted_attribute_value) @attribute.value)))
 (#eq? @tag "script"))

((script_element
  (end_tag
    (tag_name) @tag))
 (#eq? @tag "script"))

; Highlight the <style> opening/closing tags
((style_element
  (start_tag (tag_name) @tag))
 (#eq? @tag "style"))

((style_element
  (end_tag (tag_name) @tag))
 (#eq? @tag "style"))

; Highlight Scala expression delimiters { } in the template
(element
  (text) @embedded
  (#match? @embedded "\\{[^}]+\\}"))
