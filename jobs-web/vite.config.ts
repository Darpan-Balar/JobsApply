/// <reference types="vitest/config" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Built into target/classes/static so jobs-app serves it from the classpath (same origin as the API).
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "target/classes/static",
    emptyOutDir: true,
    sourcemap: false,
  },
  server: {
    // `npm run dev` against a locally running app (./mvnw -pl jobs-app spring-boot:run)
    proxy: { "/api": "http://127.0.0.1:8090" },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
