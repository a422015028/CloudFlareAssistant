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

The app detects `/** @jsx functionName */` comments and configures Sucrase accordingly.

### Module Import Resolution

The app rewrites relative imports (`./`, `../`) to `./func_N.js` format. Resolution tries:
- 7 extensions: `""`, `.js`, `.ts`, `.tsx`, `.jsx`, `.mjs`, `.cjs`
- 4 index variants: `/index.js`, `/index.ts`, `/index.tsx`, `/index.jsx`
- Fuzzy suffix matching as fallback
- Unresolved paths fall back to `./func_0.js`

All three import forms are handled: `from "..."`, `import "..."`, `import("...")`.

## Functions Standard Mode — Unsupported Features

| Feature | Reason | Workaround |
|---------|--------|------------|
| NPM dependencies | No bundler in offline environment | Copy dependency code into project, or use Workers native APIs |
| TS path aliases | `tsconfig.json` not read | Use relative paths (`../utils/types`) |
| Dynamic import variables | Static analysis only | Use static import paths |
| Node.js APIs | Workers runtime (not Node.js) | Use `nodejs_compat` compatibility flag for limited support |

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
4. **JSX needs pragma**: Add `/** @jsx h */` and import `h` function.
5. **Imports must be relative**: `import { x } from './utils.js'`, not `import { x } from 'utils'`.
6. **`_headers` and `_redirects` don't apply to Functions**: Handle in code.
7. **Static assets in `functions/` mode**: Place outside `functions/` directory at project root.
