// ESLint flat config: TypeScript, React hooks rules and basic accessibility (jsx-a11y).
// Run: npm run lint (CI runs it on every pull request). Formatting is Prettier's job.
import js from '@eslint/js';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      jsxA11y.flatConfigs.recommended,
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      // The bar chart bars are focusable SVG groups with role="img" and an accessible name.
      'jsx-a11y/no-noninteractive-tabindex': ['error', { roles: ['region', 'img'] }],
      // Native <dialog>: Escape and the backdrop click close it; the close button is a real button.
      'jsx-a11y/click-events-have-key-events': 'off',
      'jsx-a11y/no-noninteractive-element-interactions': 'off',
      // Radix radio items are buttons with role="radio" inside the label.
      'jsx-a11y/label-has-associated-control': [
        'error',
        { controlComponents: ['RadioGroup.Item'], depth: 3 },
      ],
    },
  },
);
