-- Melt language support for Neovim
--
-- Provides:
--   1. Filetype detection for .melt files
--   2. Tree-sitter syntax highlighting (HTML base + Scala/CSS injections)
--   3. LSP client configuration for melt-language-server
--
-- Prerequisites:
--   - nvim-treesitter  (with the HTML, Scala and CSS parsers installed)
--   - nvim-lspconfig
--   - melt-language-server JAR (configured via server_jar; see below)
--
-- Usage (add to init.lua or a plugin configuration file):
--   require("melt").setup({
--     server_jar = "/path/to/melt-language-server.jar",  -- optional
--     java_args  = {},                                    -- optional extra JVM args
--   })

local M = {}

-- Default configuration
local defaults = {
  server_jar = vim.fn.expand("~/.local/share/melt/melt-language-server.jar"),
  java_args  = {},
}

--- Builds the command to launch the language server.
--- @param config table resolved configuration
--- @return string[] command and arguments
local function build_cmd(config)
  local jar = config.server_jar
  if vim.fn.filereadable(jar) == 0 then
    vim.notify(
      string.format("[melt] Language server JAR not found: %s\n"
        .. "Set server_jar in require('melt').setup({})", jar),
      vim.log.levels.WARN
    )
  end
  local cmd = { "java" }
  for _, arg in ipairs(config.java_args) do
    table.insert(cmd, arg)
  end
  table.insert(cmd, "-jar")
  table.insert(cmd, jar)
  return cmd
end

--- Locates the installed tree-sitter HTML parser library on the runtimepath.
--- @return string|nil absolute path to the parser, or nil when not installed
local function find_html_parser()
  local found = vim.api.nvim_get_runtime_file("parser/html.so", false)
  if #found == 0 then
    found = vim.api.nvim_get_runtime_file("parser/html.*", false)
  end
  return found[1]
end

--- Registers `.melt` as a tree-sitter language backed by the HTML parser.
---
--- Queries are looked up by *language name*, so the buffer must parse as language
--- `melt` (not `html`) for `queries/melt/{highlights,injections}.scm` to be
--- consulted. We therefore expose the HTML parser under the `melt` language name
--- rather than mapping the filetype straight to `html` (which would load
--- `queries/html/*` and ignore our Melt queries entirely).
local function setup_treesitter()
  local html_parser = find_html_parser()
  if not html_parser then
    vim.notify(
      "[melt] tree-sitter HTML parser not found — install it (e.g. :TSInstall html). "
        .. "Melt syntax highlighting is disabled.",
      vim.log.levels.WARN
    )
    return
  end

  -- Expose the HTML parser under the `melt` language name.
  vim.treesitter.language.add("melt", { path = html_parser, symbol_name = "html" })
  -- Map the `melt` filetype to the `melt` language.
  vim.treesitter.language.register("melt", "melt")

  -- Make queries/melt/*.scm discoverable on the runtimepath. This file lives at
  -- neovim/lua/melt.lua, so its grandparent is the neovim/ directory that
  -- contains queries/.
  local plugin_root = vim.fn.fnamemodify(debug.getinfo(1, "S").source:sub(2), ":p:h:h")
  vim.opt.runtimepath:append(plugin_root)

  -- Start tree-sitter highlighting for every .melt buffer using the `melt`
  -- language (so our queries load).
  vim.api.nvim_create_autocmd("FileType", {
    pattern = "melt",
    callback = function(args)
      pcall(vim.treesitter.start, args.buf, "melt")
    end,
  })
end

--- Sets up Melt language support.
---
--- @param opts table|nil Optional overrides for the default configuration.
function M.setup(opts)
  local config = vim.tbl_deep_extend("force", defaults, opts or {})

  -- 1. Filetype detection ──────────────────────────────────────────────────
  vim.filetype.add({
    extension = { melt = "melt" },
  })

  -- 2. Tree-sitter ─────────────────────────────────────────────────────────
  setup_treesitter()

  -- 3. LSP client ──────────────────────────────────────────────────────────
  local ok, lspconfig = pcall(require, "lspconfig")
  if not ok then
    vim.notify("[melt] nvim-lspconfig is required for LSP support", vim.log.levels.WARN)
    return
  end

  local configs = require("lspconfig.configs")

  if not configs.melt then
    configs.melt = {
      default_config = {
        cmd = build_cmd(config),
        filetypes = { "melt" },
        root_dir = lspconfig.util.root_pattern("build.sbt", ".git"),
        single_file_support = true,
        settings = {},
      },
    }
  end

  lspconfig.melt.setup({
    on_attach = function(client, bufnr)
      -- Standard LSP keymaps (can be overridden by user)
      local opts = { buffer = bufnr, noremap = true, silent = true }
      vim.keymap.set("n", "K",          vim.lsp.buf.hover,           opts)
      vim.keymap.set("n", "gd",         vim.lsp.buf.definition,      opts)
      vim.keymap.set("n", "<leader>ca", vim.lsp.buf.code_action,     opts)
      vim.keymap.set("n", "<leader>rn", vim.lsp.buf.rename,          opts)
    end,
  })
end

return M
