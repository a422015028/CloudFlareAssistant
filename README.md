# CloudFlareAssistant

English | [简体中文](README_CN.md)

An Android app for managing Cloudflare services — deploy Workers/Pages, manage DNS, D1, R2, KV, Zero Trust, and more, all from your phone.

## Why

Cloudflare's dashboard is desktop-first. This app puts the core workflow on mobile: write a Worker, deploy a Pages project, check DNS records, query D1 — without opening a browser.

## Features

### Deploy from Phone
- **Workers** — Upload, edit, deploy scripts with custom domains and route rules
- **Pages** — Deploy zip/JS/HTML projects with on-device TypeScript/JSX compilation (Sucrase) and NPM package bundling (esbuild-wasm), no CI/CD needed
- **Built-in Code Editor** — Syntax-highlighted editor (CodeMirror) with version history and unsaved-change detection

### Cloudflare Services
| Service | Capabilities |
|---------|-------------|
| **Workers** | Script upload (multipart/content/legacy), settings, custom domains, routes, build triggers, deployment history, real-time logs |
| **Pages** | Project list, deployments, custom domains, environment variables, D1 bindings, deployment logs |
| **DNS & Zones** | Zone list, add domains, DNS record CRUD, zone settings (cache, SSL, WAF, rate limit, transform rules, snippets, load balancers, email routing) |
| **D1** | Database list, table viewer, SQL execution |
| **R2** | Bucket browse, file upload/download via S3-compatible API |
| **KV** | Namespace management, key-value CRUD |
| **Zero Trust** | Access apps/policies/groups, device management, gateway rules/lists/locations, tunnel connections |

### Convenience
- **Multi-account** — Switch between Cloudflare accounts with a tap
- **Dual Auth** — API Token or Global API Key
- **Backup** — Export account configs to WebDAV or R2
- **Dashboard** — Account analytics with time-range filtering
- **In-app Update** — Check and install new versions directly
- **Offline-first Editor** — Write and save code locally, deploy when ready

## Tech Stack

Kotlin · Hilt · MVVM · Coroutines/Flow · Room · Retrofit/OkHttp · Navigation Component · Material 3 · Sucrase · esbuild-wasm

## Screenshots

<!-- Add screenshots here -->

## Download

Download the latest APK from [Releases](../../releases) or visit [cf.390202.xyz](https://cf.390202.xyz).

## Requirements

- Android 8.0+ (API 26)
- A Cloudflare account (API Token or Global API Key)
- Network connection for API calls; esbuild-wasm download (~12MB) required for first NPM-bundled Pages deployment

## License

MIT
