/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,jsx}"
    ],
    theme: {
        extend: {
            colors: {
                pulse: {
                    dark: '#0a0a0f',
                    card: '#12121a',
                    border: '#1e1e2e',
                    accent: '#ff4d6d',
                    secondary: '#7c3aed',
                    success: '#10b981',
                }
            },
            animation: {
                'pulse-glow': 'pulse-glow 2s ease-in-out infinite',
                'slide-in': 'slide-in 0.3s ease-out',
            },
            keyframes: {
                'pulse-glow': {
                    '0%, 100%': { boxShadow: '0 0 20px rgba(255, 77, 109, 0.3)' },
                    '50%': { boxShadow: '0 0 40px rgba(255, 77, 109, 0.6)' },
                },
                'slide-in': {
                    '0%': { transform: 'translateX(100%)', opacity: '0' },
                    '100%': { transform: 'translateX(0)', opacity: '1' },
                }
            }
        },
    },
    plugins: [],
}
