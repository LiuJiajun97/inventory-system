import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// dev 代理 /api 到后端 8081(Java 版;Fastify 旧版仍在 8080,保留可回滚)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8081",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
    sourcemap: false,
  },
});
