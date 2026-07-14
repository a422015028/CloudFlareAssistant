---
name: "pages-deploy-guide"
description: "Guides Cloudflare Pages project creation and deployment via the CloudFlareAssistant Android app. Invoke when user asks how to write/structure a Pages project for deployment, or needs to know supported/unsupported features."
---

# Pages Deploy Guide (CloudFlareAssistant)

Guide for writing Cloudflare Pages projects that deploy correctly through the CloudFlareAssistant Android app.

## Deployment Modes

The app supports 5 deployment modes, selected automatically by file type and structure:

| Mode | Trigger | Description |
|------|---------|-------------|
| Single `.html` file | Upload `*.html` or `*.htm` | Deploys as `/index.html` static asset |
| Single `.js` file | Upload `*.js` | Deploys as `_worker.js` (Module Worker syntax) |
| `_worker.js` single file | Zip contains `/_worker.js` at root | Advanced mode: full request control via `export default { fetch }` |
| `functions/` standard mode | Zip contains `/functions/` directory | File-based routing with TypeScript/JSX support |
| Pure static | Zip with only static assets | HTML/CSS/JS/images served directly |

**Priority order**: `_worker.js` > `functions/` > pure static.

**Not supported**: `_worker.js/` directory mode (Cloudflare direct upload API does not support it).

## Project Structure

### Pure Static
```
project.zip
├── index.html
├── style.css
├── app.js
└── images/
    └── logo.png
```

### `_worker.js` Advanced Mode
```
project.zip
├── _worker.js      ← Module Worker syntax (export default { fetch })
├── index.html       ← static assets (optional)
└── assets/
```

### `functions/` Standard Mode
```
project.zip
├── functions/
│   ├── index.js              → /
│   ├── hello.js              → /hello
│   ├── api/
│   │   ├── time.js           → /api/time
│   │   ├── echo.js           → /api/echo
│   │   └── user/
│   │       └── [id].js       → /api/user/:id (dynamic route)
│   ├── [[slug]].js           → /:slug* (catch-all route)
│   └── _middleware.js        → middleware for all routes
├── index.html                ← static assets (optional)
└── _routes.json              ← optional, auto-generated if absent
```

### Nested Directory Traversal

If the zip contains a single top-level wrapper directory (e.g., `myproject/`), the app automatically traverses into it. Exceptions: `_worker.js` and `functions` directories are never traversed into. `public/` directories ARE traversed.

## Functions Standard Mode — Supported Features

| Feature | Supported | Notes |
|---------|-----------|-------|
| File-based routing | Yes | `hello.js` → `/hello` |
| Dynamic routes `[param]` | Yes | Single path segment matching |
| Catch-all `[[param]]` | Yes | Multi-path segment matching, returns array |
| Middleware `_middleware.js` | Yes | `context.next()` chaining, sorted by mount path depth |
| Method-specific exports | Yes | `onRequestGet`, `onRequestPost`, `onRequestPut`, `onRequestPatch`, `onRequestDelete`, `onRequestOptions`, `onRequestHead` |
| `onRequest` (all methods) | Yes | Fallback when no method-specific export |
| TypeScript `.ts` | Yes | Sucrase type stripping (offline, no bundler) |
| TSX `.tsx` | Yes | Sucrase + JSX transform |
| JSX `.jsx` | Yes | Sucrase + JSX transform |
| Module imports | Yes | Relative paths auto-rewritten to `./func_N.js` |
| NPM packages | Yes | esbuild-wasm bundles bare imports via esm.sh CDN (on-demand download) |
| Static asset fallback | Yes | `env.ASSETS.fetch(request)` |
| `_routes.json` | Yes | Auto-generated or user-provided |

### JSX Pragma

JSX transforms default to `React.createElement`. Since Workers runtime has no React, use a custom pragma:

```javascript
/** @jsx h */
import { h } from './utils/helpers.js'

export function onRequestGet(context) {
  return new Response(<div>Hello</div>)
}
```

The app detects `/** @jsx functionName */` comments and configures both Sucrase and esbuild accordingly.

**Critical**: esbuild scans ALL comments in the source file for `@jsx` directives. Source-file `@jsx` pragma takes **precedence** over the `jsxFactory` build option. Never put `@jsx` in any comment other than the actual pragma directive — a comment like `// 验证 @jsx pragma 生效` will be interpreted as a pragma, overriding the intended factory function and causing `ReferenceError` at runtime.

### Module Import Resolution

The app rewrites relative imports (`./`, `../`) to `./func_N.js` format. Resolution tries:
- 7 extensions: `""`, `.js`, `.ts`, `.tsx`, `.jsx`, `.mjs`, `.cjs`
- 4 index variants: `/index.js`, `/index.ts`, `/index.tsx`, `/index.jsx`
- Fuzzy suffix matching as fallback
- Unresolved paths fall back to `./func_0.js`

All three import forms are handled: `from "..."`, `import "..."`, `import("...")`.

### NPM Package Support (esbuild-wasm)

Files containing **bare imports** (e.g., `import React from 'react'`) are automatically routed to esbuild-wasm for bundling. The app detects bare imports — any import specifier that is NOT a relative path (`./`, `../`), absolute path (`/`), or URL (`http://`, `https://`).

**How it works:**
1. App detects bare imports in function files (any extension: `.js`, `.ts`, `.tsx`, `.jsx`)
2. esbuild-wasm files are downloaded on-demand (not bundled in APK) from jsdelivr CDN
3. esbuild bundles each file: inlines NPM dependencies from esm.sh CDN, transforms TS/JSX
4. Relative imports (`./`, `../`) are marked as `external` — preserved for the import rewriter
5. Output is self-contained JS with NPM code inlined + relative imports preserved

**Example:**
```typescript
// functions/api/qr.ts — uses NPM package
import QRCode from 'qrcode'

export function onRequestGet() {
  const data = QRCode.toDataURL('hello')
  return new Response(`<img src="${data}">`)
}
```

**Requirements:**
- Network connection required (esbuild-wasm download + esm.sh CDN fetch)
- First use triggers ~12MB download (esbuild-wasm.js + esbuild.wasm), cached afterward
- Files without bare imports still use Sucrase (offline, 193KB, no download needed)

**Dual-path architecture:**
- Files WITH bare imports → esbuild-wasm (NPM bundling + TS/JSX transform)
- Files WITHOUT bare imports → Sucrase (TS/JSX transform only, offline)

**Version management:**
- App queries npm registry (`registry.npmjs.org/esbuild-wasm/latest`) for latest version
- Local cached version compared against latest; re-downloads if outdated
- Version cached in app private storage (`esbuild/version.txt`)

**Multi-CDN download:**
- jsdelivr (`cdn.jsdelivr.net`) as primary CDN, unpkg (`unpkg.com`) as fallback
- Downloads `esm/browser.min.js` (JS) and `esbuild.wasm` (WASM binary) separately
- Files stored in app private storage (`filesDir/esbuild/`), never in public directories

**esm.sh CDN integration:**
- Bare imports resolved via `https://esm.sh/{package}?bundle`
- `?bundle` parameter returns self-contained single file — inlines all submodule dependencies (e.g., crypto-js's internal `./core` imports)
- CDN module relative imports resolved against final redirected URL
- Relative imports from original source file marked as `external` (not sent to CDN)

**WebView integration:**
- esbuild-wasm runs inside Android WebView with `worker: false` for compatibility
- `shouldInterceptRequest` intercepts local WASM/JS files from private storage
- CDN fetches (esm.sh) pass through unintercepted
- Dynamic `import()` loads esbuild ESM module (avoids `<script type="module">` scope issues)

**JSX pragma detection:**
- App detects `/** @jsx h */` comments and sets esbuild's `jsxFactory` option
- Also detects `/** @jsxFrag Fragment */` for fragment pragma
- **Warning**: esbuild also scans ALL source comments for `@jsx` — see Common Pitfalls #5

## Functions Standard Mode — Unsupported Features

| Feature | Reason | Workaround |
|---------|--------|------------|
| TS path aliases | `tsconfig.json` not read | Use relative paths (`../utils/types`) |
| Dynamic import variables | Static analysis only | Use static import paths |
| Node.js APIs | Workers runtime (not Node.js) | Use `nodejs_compat` compatibility flag for limited support |
| esbuild RE2 regex lookahead | esbuild's onResolve/onLoad filters use Go RE2 engine (no negative lookahead) | Split into sequential callback handlers |

## Static Asset Features

### `.assetsignore`

Exclude files from asset upload using glob patterns:

```
# Comments start with #
README.md
*.md
docs/**
LICENSE
.gitignore
```

Supported glob patterns:
- `file.md` — match root-level file
- `*.md` — match root-level `.md` files
- `**/*.md` — recursively match all `.md` files
- `docs/*` — match files directly under `docs/`
- `docs/**/*.md` — recursively match `.md` under `docs/`

### `_headers` and `_redirects`

Uploaded as static assets. Cloudflare automatically parses them. Note: these do NOT apply to Pages Functions responses — handle headers/redirects in Function code directly.

### `_routes.json`

Controls which paths invoke Functions vs static assets. Auto-generated if absent:

```json
{
  "version": 1,
  "include": ["/*"],
  "exclude": ["/static/*"]
}
```

User-provided `_routes.json` takes priority. Place at project root.

## Project Configuration

### Compatibility Date

Set via UI input field. Default: `2026-06-16`. Applied to both production and preview environments.

### Compatibility Flags

Set via UI input field (comma or newline separated). Example: `nodejs_compat`. Applied via PATCH project API.

Common flags:
- `nodejs_compat` — enables Node.js compatibility layer
- `experimental_global_cjs_imports` — CommonJS import support

### Bindings (via Project Settings API)

All bindings are configured through the app's project settings, not in deployment files:

- Environment variables (`env_vars`)
- KV namespaces (`kv_namespaces`)
- R2 buckets (`r2_buckets`)
- D1 databases (`d1_databases`)
- Durable Objects (`durable_objects`)
- Service bindings (`services`)

### Asset Config

- `not_found_page` — custom 404 page path
- `pretty_urls` — enable/disable trailing slash normalization

## File Type Restrictions

The app accepts uploads of:
- `.zip` — project archive (all modes)
- `.js` — single Worker script
- `.htm` / `.html` — single static page

## API Flow

The app follows the Wrangler direct upload flow:

1. Get upload JWT token
2. Upload assets as Base64 to asset store
3. Upsert asset hashes
4. Create deployment with manifest:
   - Pure static: `manifest` only
   - `_worker.js`: `manifest` + `_worker.bundle` (nested multipart with metadata + module)
   - `functions/`: `manifest` + `_worker.bundle` + `functions-filepath-routing-config.json` + `_routes.json`

## Common Pitfalls

1. **`_worker.js` must use Module syntax**: `export default { async fetch(request, env) { ... } }`, not `addEventListener('fetch', ...)`.
2. **Functions must export `onRequest`**: or method-specific variants like `onRequestGet`.
3. **No `_worker.js/` directory**: Use single file or `functions/` mode instead.
4. **JSX needs pragma**: Add `/** @jsx h */` at file top and define/import `h` function.
5. **Never put `@jsx` in comments**: esbuild scans ALL comments for `@jsx` directives. A comment like `// 验证 @jsx pragma` will be misinterpreted as a pragma, overriding `@jsx h` and causing `ReferenceError`. Only use `@jsx` in the actual pragma directive.
6. **NPM packages need network**: Bare imports trigger esbuild-wasm (requires download + CDN access). Relative imports work offline via Sucrase.
7. **`_headers` and `_redirects` don't apply to Functions**: Handle in code.
8. **Static assets in `functions/` mode**: Place outside `functions/` directory at project root.
9. **QR code async**: `QRCode.toDataURL()` returns a Promise — must `await` it, otherwise output shows `[object Promise]`.
