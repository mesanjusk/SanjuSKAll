# SK Ship — Merchant Shipping Platform (MVP)

SK Ship is an early-stage courier-aggregation platform built with **Next.js 16 (App Router), TypeScript, MongoDB** and designed for deployment as a **Render Node web service**.

> **Important:** This is a working application foundation, not a live courier aggregator. It is deliberately safe by default: **no real courier bookings, shipping rates, payments, AWBs, wallet credits or COD payouts are available** until contract-approved carrier and payment integrations are implemented and tested.

## What's included

- Branded, responsive public website with feature, pricing and tracking pages
- Merchant registration/login backed by MongoDB, bcrypt password hashes and signed HTTP-only sessions
- Merchant dashboard and **shipment draft** creation (not courier booking)
- Private per-merchant shipment data; role-protected admin overview
- Basic registration/login rate limiting and same-origin mutation checks
- Public tracking endpoint, returning only confirmed and existing non-draft shipment statuses
- Health endpoint (`/api/health`), database readiness check (`/setup`)
- Render deployment config and environment template

## Local setup

Requirements: Node.js 22+, npm, MongoDB Atlas or compatible MongoDB.

```bash
npm install
cp .env.example .env.local
# Set MONGODB_URI, MONGODB_DB and a randomly generated SESSION_SECRET (at least 32 characters)
npm run dev
```

With no `MONGODB_URI`, the public website and `/api/health` run in honest `setup` mode. Registration and authenticated business features return a clear service-unavailable message.

### Admin bootstrap

Do not self-assign an admin role through public registration. Create the first admin *only from a trusted maintenance shell*, after configuring a database:

```bash
ADMIN_EMAIL='owner@example.com' ADMIN_PASSWORD='choose-a-strong-16+-character-password' \
MONGODB_URI='mongodb+srv://...' MONGODB_DB='sk_ship' npm run seed:admin
```

The script refuses to upgrade an existing user automatically. Keep admin credentials and the Mongo URI **out of Git**.

## Render deployment

Create a **Node web service** from this repository (or use `render.yaml` Blueprint):

- Region: Singapore (suitable for Indian users)
- Build: `npm install && npm run build`
- Start: `npm start`
- Health: `/api/health`
- Environment: `NODE_VERSION=22.16.0`, `MONGODB_DB=sk_ship`, `SESSION_SECRET=<random strong value>`
- Later set: `MONGODB_URI=<your Atlas URI>` via **Render Environment**, never source control.

A public preview can be deployed now without MongoDB. **Before opening merchant sign-ups**, add the database secret, restrict Atlas network exposure, verify indices, and test sessions and backup/restore.

## Roadmap (not yet implemented)

1. Written courier aggregator/reseller agreements, per-carrier serviceability and real rate lookup.
2. Booking APIs, AWB and label creation, pickup/NDR/tracking webhooks, idempotency and queue workers.
3. Financially correct prepaid wallet ledger, verified payment processor and COD settlement reconciliation.
4. Merchant KYC/GST, GST invoicing, RTO, weight discrepancy handling and support workflows.
5. Monitoring, alerts, backups, invoice/report export, role permissions and security audit.

## Security / production notes

- Currently **not approved for commercial use or customer funds**.
- Use a dedicated MongoDB user limited to `sk_ship`; do not reuse production MIS database credentials.
- Add email verification, password recovery, stronger risk controls, CSRF tests, monitoring and continuous security review before public launch.
- Prefer Render paid instances for sustained production load; free tiers sleep and have resource limits.
- Stripe/other payments and courier partners are intentionally **not connected**.

© SK Ship — S K Digital.
