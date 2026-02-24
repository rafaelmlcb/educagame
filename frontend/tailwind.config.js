/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        dark: '#0a0f1e',
        primary: '#10b981',
        secondary: '#3b82f6',
        glass: 'rgba(15, 23, 42, 0.8)',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        card: '24px',
        button: '12px',
      },
      backdropBlur: {
        glass: '12px',
      },
      boxShadow: {
        glow: '0 0 40px rgba(16, 185, 129, 0.15)',
        card: '0 8px 32px rgba(0,0,0,0.4)',
      },
    },
  },
  plugins: [],
  corePlugins: {
    preflight: false,
  },
}
