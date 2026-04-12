/**
 * Vite configuration for the http4s-ssr example's production build.
 *
 * In dev mode, Vite is NOT used — sbt + fastLinkJS directly serves the
 * un-hashed Scala.js output. This configuration is only invoked when
 * running `npm run build` (or `npx vite build`) to produce optimised,
 * content-hashed assets for production deployment.
 *
 * == How the input entries are wired ==
 *
 * Instead of hard-coding each component in `rollupOptions.input`, we
 * read an sbt-generated JSON file (`target/vite-inputs.json`) that
 * maps each component's moduleID to its Scala.js linker output path.
 * This file is written by the `meltcViteInputGenerate` sbt task,
 * which reads `Report.publicModules` after `fullLinkJS` completes.
 *
 * Adding or removing a `.melt` file automatically updates the input
 * map on the next `fullLinkJS` run — no manual edits to this file.
 */
import { readFileSync } from "node:fs";
import { defineConfig } from "vite";

// sbt writes this after fullLinkJS completes. Each key is a moduleID
// ("home", "about", …) and each value is the absolute path to the
// corresponding .js file in the fullLinkJS output directory.
const inputsPath = new URL(
  "components/js/target/vite-inputs.json",
  import.meta.url
);
let rollupInput;
try {
  rollupInput = JSON.parse(readFileSync(inputsPath, "utf-8"));
} catch (e) {
  console.error(
    `[melt] Could not read ${inputsPath.pathname}.\n` +
    `       Run  sbt http4s-ssr-server/meltcViteInputGenerate  first.\n`,
    e.message
  );
  process.exit(1);
}

export default defineConfig({
  build: {
    // Write output to examples/http4s-ssr/dist/ so the server can
    // serve it via fileService without path gymnastics.
    outDir: "dist",

    // Emit a .vite/manifest.json so the server can map moduleIDs to
    // hashed filenames at runtime.
    manifest: true,

    rollupOptions: {
      input: rollupInput,

      // Preserve named exports from entry modules. Without this,
      // Rollup strips the `export { hydrate }` from each component's
      // entry chunk, causing "hydrate is not a function" errors.
      preserveEntrySignatures: "exports-only",

      output: {
        entryFileNames: "assets/[name]-[hash].js",
        chunkFileNames: "assets/[name]-[hash].js",
        assetFileNames: "assets/[name]-[hash][extname]",
      },
    },
  },
});
