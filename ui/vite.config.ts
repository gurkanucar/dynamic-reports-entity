import { resolve } from "node:path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { defineConfig } from "vite"

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    // The Spring Boot API; same origin for the browser, so no CORS setup is needed.
    proxy: {
      "/api": { target: process.env.API_URL ?? "http://localhost:8080", changeOrigin: true },
    },
  },
})
