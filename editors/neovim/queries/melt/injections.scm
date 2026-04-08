; Tree-sitter injection queries for .melt files.
;
; These queries require nvim-treesitter with the HTML parser installed.
; The HTML tree-sitter grammar is used as the base; Scala and CSS are injected
; into the respective <script> and <style> blocks.
;
; Install with:
;   require("nvim-treesitter.configs").setup {
;     ensure_installed = { "html", "scala", "css" },
;     ...
;   }

; Inject Scala into <script lang="scala"> blocks
((script_element
  (start_tag
    (tag_name) @_tag
    (attribute
      (attribute_name) @_lang_name
      (quoted_attribute_value
        (attribute_value) @_lang_val)))
  (raw_text) @injection.content)
 (#eq? @_tag "script")
 (#eq? @_lang_name "lang")
 (#eq? @_lang_val "scala")
 (#set! injection.language "scala")
 (#set! injection.combined))

; Inject CSS into <style> blocks
((style_element
  (raw_text) @injection.content)
 (#set! injection.language "css")
 (#set! injection.combined))
