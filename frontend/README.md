# ShipStream frontend

A minimal React + TypeScript UI over the ShipStream API: a shipments list, a shipment detail/live-tracking
view, a create-shipment form, and a hubs & routes admin screen.

See [`src/styles/theme.css`](src/styles/theme.css) for the whole design system - every page reads from
the same set of CSS custom properties, so there's one theme, not four.

## Run locally

Backend must be running first (see the [root README](../README.md)):

```bash
docker compose up -d        # from the repo root
./mvnw spring-boot:run      # from the repo root
```

Then, from this directory:

```bash
npm install
npm run dev        # http://localhost:5173
```

The dev server proxies nothing - it just calls the backend directly at `http://localhost:8080` (CORS is
already configured there for `5173`). Override with `VITE_API_URL` if your backend runs elsewhere.

## Stack

- **Vite + React + TypeScript** - no framework beyond what's needed for a 4-page app.
- **TanStack Query** - data fetching, caching, loading/error states; no hand-rolled request plumbing.
- **`@stomp/stompjs` + `sockjs-client`** - subscribes to `/topic/shipments/{trackingId}` on the shipment
  detail page for live status/location updates, falling back to polling if the socket drops.
- **Plain CSS** - no component library, no utility-class framework. One stylesheet of design tokens
  (`theme.css`), everything else reads from it.

## Build

```bash
npm run build      # type-checks, then builds to dist/
npm run preview    # serves the production build on :4173, for a final local check
```
