import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// dev 代理 /api 到后端 8888(2026-09-14 从 8081 迁出,用户 8081 另有用途)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8888",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: false,
  },
});
