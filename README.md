# LogiTrust

Landing page for **LogiTrust** — an AI-powered supply chain integrity platform that tracks goods across manufacturers, distributors, couriers, retailers and customers, treating every handoff as a security event.

The hero is a scroll-driven animation: a package travels through a 5-stage supply chain journey (Manufacturer → Route → Fraud Checkpoint → Admin Resolution → Delivered) as you scroll, scrubbed frame-by-frame on a canvas like Apple's product pages.

## Stack

- [Next.js](https://nextjs.org) (App Router) + TypeScript
- [Motion](https://motion.dev) (Framer Motion) — `useScroll` + `useTransform` for scroll-linked frame scrubbing and overlay fades
- Canvas-based rendering over a 320-frame JPG sequence in `public/frames/`

## How the hero works

- A 400vh wrapper drives scroll progress; a `position: sticky` canvas stays pinned while scroll progress maps linearly to a frame index (1–320).
- Frames draw imperatively from the motion value's change callback — no React state per scroll tick, so scrubbing is smooth in both directions.
- Frames 1–80 preload first (with a progress bar) so interaction starts early; the remaining 240 stream in the background.
- Text overlays fade in/out at fixed scroll-progress windows, including a risk score that ticks 0 → 87 during the fraud-checkpoint beat.

## Getting started

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## Scripts

| Command | Description |
| --- | --- |
| `npm run dev` | Start the dev server |
| `npm run build` | Production build |
| `npm start` | Serve the production build |
| `npm run lint` | Lint |
