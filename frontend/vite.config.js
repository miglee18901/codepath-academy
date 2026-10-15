import {defineConfig, loadEnv} from 'vite'
import react from '@vitejs/plugin-react'
import process from 'node:process'

export default defineConfig(({mode}) => {
    const env = loadEnv(mode, process.cwd(), '');
    const publicUrl = env.FRONTEND_PUBLIC_URL || 'http://localhost:5173';
    return {
        plugins: [react()],
        server: {
            allowedHosts: [new URL(publicUrl).hostname],
            proxy: {
                '/api': {
                    target: env.API_PROXY_TARGET || 'http://localhost:8080',
                    changeOrigin: true,
                },
            },
        },
    };
})
