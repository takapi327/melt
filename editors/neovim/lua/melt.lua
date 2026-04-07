-- Melt language support for Neovim
--
-- Provides:
--   1. Filetype detection for .melt files
--   2. Tree-sitter syntax highlighting (HTML base + Scala/CSS injections)
--   3. LSP client configuration for melt-language-server
--
-- Prerequisites:
--   - nvim-treesitter  (with HTML, Scala, CSS parsers)
--   - nvim-lspconfig
--   - melt-language-server JAR on PATH or configured via melt_server_jar
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
  -- Associate .melt files with the HTML parser (injections handle Scala/CSS).
  -- The queries directory is expected at <this-file's-parent>/../queries/melt/
  local queries_dir = vim.fn.fnamemodify(
    debug.getinfo(1, "S").source:sub(2),  -- absolute path to this file
    ":p:h:h"                              -- parent of lua/ = neovim/
  ) .. "/queries"

  -- Register the parser association
  vim.treesitter.language.register("html", "melt")

  -- Add the custom query path so nvim-treesitter can find our injections.scm
  -- and highlights.scm
  vim.opt.runtimepath:append(vim.fn.fnamemodify(queries_dir, ":h"))

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

return M
