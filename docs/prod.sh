#!/usr/bin/env bash
# prod.sh — Build and start the docs SSR server in production mode.
#
# Prerequisites (run once from the monorepo root):
#   sbt "compiler-cssJVM/publishLocal; compiler-cssJS/publishLocal; \
#        compilerJVM/publishLocal; compilerJS/publishLocal; \
#        runtimeJVM/publishLocal; runtimeJS/publishLocal; \
#        codegenJVM/publishLocal; codegenJS/publishLocal; \
#        meltkitJVM/publishLocal; meltkitJS/publishLocal; \
#        meltkit-adapter-browser/publishLocal; \
#        sbt-meltc/publishLocal; sbt-meltkit/publishLocal"
#
# Usage:
#   ./prod.sh            # build + start server
#   ./prod.sh --build    # build only (skip server start)
#   ./prod.sh --serve    # start server without rebuilding

set -euo pipefail

DOCS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BUILD=true
SERVE=true

for arg in "$@"; do
  case "$arg" in
    --build) SERVE=false ;;
    --serve) BUILD=false ;;
    *) echo "Unknown option: $arg"; exit 1 ;;
  esac
done

cd "$DOCS_DIR"

if $BUILD; then
  # Step 1: Build @melt/vite-plugin (generates packages/vite-plugin/dist)
  echo "[1/3] Building @melt/vite-plugin..."
  pnpm --filter @melt/vite-plugin build

  # Step 2: Compile Scala.js (fullLinkJS) and generate vite-inputs.json
  echo "[2/3] Compiling Scala.js and generating vite-inputs.json..."
  sbt "docsJVM/meltkitViteInputGenerate"

  # Step 3: Bundle JS/CSS with Vite → docs/dist
  echo "[3/3] Building with Vite..."
  pnpm exec vite build
fi

if $SERVE; then
  echo "Starting SSR server (MELT_PROD=true)..."
  MELT_PROD=true sbt "docsJVM/run server"
fi
