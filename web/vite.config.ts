import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// 生产挂载在 80 端口 /inventory 子路径下(同 IP 多项目按路径共享 80):
// base 让静态资源带 /inventory 前缀;dev 下 vite 也按此前缀提供,不影响本地 5173
export default defineConfig({
  base: "/inventory/",
  plugins: [react()],
  server: {
    port: 5173,
    // dev 代理 /inventory/api 到后端 8888(2026-09-14 从 8081 迁出,用户 8081 另有用途)
    proxy: {
      "/inventory/api": {
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
