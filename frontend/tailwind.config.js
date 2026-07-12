/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: ['class'],
  content: ['./src/**/*.{html,ts}'],
  theme: {
    container: {
      center: true,
      padding: '1rem'
    },
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))'
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))'
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))'
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))'
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))'
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))'
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))'
        },
        /* Tweebyte brand + intent accents (X-grade). */
        brand: {
          DEFAULT: 'hsl(var(--brand))',
          foreground: 'hsl(var(--brand-foreground))'
        },
        like: 'hsl(var(--like))',
        retweet: 'hsl(var(--retweet))',
        reply: 'hsl(var(--reply))',
        warn: 'hsl(var(--warn))'
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
        full: '9999px'
      },
      fontFamily: {
        sans: [
          '-apple-system',
          'BlinkMacSystemFont',
          'Inter',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif'
        ]
      },
      keyframes: {
        'like-burst': {
          '0%': { transform: 'scale(1)' },
          '40%': { transform: 'scale(1.45)' },
          '100%': { transform: 'scale(1)' }
        },
        'count-tick': {
          '0%': { transform: 'translateY(40%)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' }
        },
        'caret-blink': {
          '0%,70%,100%': { opacity: '1' },
          '20%,50%': { opacity: '0' }
        },
        'fade-in': {
          '0%': { opacity: '0', transform: 'translateY(4px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' }
        },
        shimmer: {
          '100%': { transform: 'translateX(100%)' }
        },
        'pulse-dot': {
          '0%,100%': { opacity: '1' },
          '50%': { opacity: '0.35' }
        }
      },
      animation: {
        'like-burst': 'like-burst 0.4s ease-out',
        'count-tick': 'count-tick 0.25s ease-out',
        'caret-blink': 'caret-blink 1.1s step-end infinite',
        'fade-in': 'fade-in 0.28s ease-out',
        shimmer: 'shimmer 1.6s infinite',
        'pulse-dot': 'pulse-dot 1.4s ease-in-out infinite'
      }
    }
  },
  plugins: [require('tailwindcss-animate')]
};
