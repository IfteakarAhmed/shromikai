import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // Forward API calls to the Spring Boot backend during development
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
