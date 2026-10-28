import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  // sockjs-client assumes a Node-style `global` object; unlike webpack, Vite doesn't polyfill it.
  // Without this, the app throws "global is not defined" at import time and never renders anything.
  // Needs to be set in both places: `define` for app code, `optimizeDeps.esbuildOptions.define` for
  // Vite's separate dependency pre-bundling step (sockjs-client gets pre-bundled, so it only sees
  // the latter unless both are set).
  define: {
    global: "globalThis",
  },
  optimizeDeps: {
    esbuildOptions: {
      define: {
        global: "globalThis",
      },
    },
  },
  server: {
    port: 5173,
  },
});
