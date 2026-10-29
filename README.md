# MannVeedu — Tamil Nadu Land Marketplace

A responsive, map-led land marketplace with account registration/login, database-backed plot listings, saved plots, comparisons, and contact/site-visit requests. Frontend assets are served by Spring Boot from `src/main/resources/static`.

## Run

Requires Java 17+ and Maven 3.6.3+.

```sh
mvn spring-boot:run
```

Open [http://localhost:8081](http://localhost:8081) (set `SERVER_PORT` to change it). The REST API is rooted at `/api`.

## Database

The app connects to your PostgreSQL server at `localhost:5432`. The initial eight fictional Tamil Nadu plots are inserted when the plots table is empty. Accounts are registered in the app; passwords are BCrypt-hashed. Saved plots, owner listings, and enquiries are persisted too.

The server is reachable on `localhost:5432`. Local defaults are database `mannveedu`, user `postgres`, and password `1234`. Override them with `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD` in your IDE run configuration when needed. The included password and superuser account are for local development only; use a dedicated least-privilege role and secret credentials outside local development.

## Marketplace management

- New owner listings and edits enter the admin review queue. They appear publicly after approval.
- To provision an admin account locally, first register the intended account normally, then run `UPDATE users SET role='ADMIN' WHERE email='your-admin-login@example.com';` in the `mannveedu` PostgreSQL database. Sign out and back in to load the Admin dashboard. Do not expose this database account to the public internet.
- Owners can upload JPG, PNG, or WebP listing photos (up to 8 MB each). Files are stored under `UPLOAD_DIR` (defaults to `uploads` in the app working directory); back up this folder along with the database.
- Buyers can filter by min/max price, plot size, facing, and approval. Public owner profiles show their approved active listings.
- Owners can confirm, reschedule, cancel, or mark a site visit complete from the owner dashboard. Buyers see the latest status/date in their dashboard and receive an update at their contact email when available.
- Admins can track support queries as Open, In progress, or Resolved.
- Visitors can use “Help / Send a query” to send a support message. Queries are stored in PostgreSQL, emailed to the configured admin address when SMTP is available, and visible in the admin dashboard.

## Enquiry email notifications

Enquiries and site-visit requests are always saved in PostgreSQL. The app sends a notification to the listing owner’s account email and a lead email to `s.raghul3001@gmail.com` when SMTP is configured. For Gmail/Google Workspace, set these environment variables in the IDE run configuration: `SMTP_USERNAME` (mail-sending account), `SMTP_PASSWORD` (Google App Password), and optionally `ADMIN_EMAIL` (defaults to the address above). `SMTP_HOST` defaults to `smtp.gmail.com`, and `SMTP_PORT` to `587`. Do not use or store your normal Gmail password in the project. Delivery attempts and failures are recorded in `notification_log`; the submitter sees whether mail was sent or SMTP still needs setup.

## API

- `GET /api/plots` — optional `city`, `type`, `minPrice`, `maxPrice`, `minAcres`, `maxAcres`, `approved`, `q` filters
- `GET /api/plots/{id}` — plot detail
- `POST /api/auth/register` — create a buyer or owner account
- `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout` — session sign-in
- `GET /api/saved-plots`, `POST|DELETE /api/saved-plots/{plotId}` — account saved plots
- `GET /api/my-plots`, `POST /api/plots`, `PUT|DELETE /api/plots/{id}` — create, update, and remove owner listings
- `GET /api/my-plots/review-status`, `GET /api/admin/listings/pending`, `PUT /api/admin/listings/{id}/review` — review queue and moderation
- `POST /api/plots/{id}/photos`, `GET /api/plots/{id}/photos`, `GET /api/plots/{id}/owner` — plot photos and owner profile
- `GET /api/owners/{email}` — public owner profile with active listings
- `POST /api/enquiries` — save contact or site-visit request and send configured email notifications
- `GET /api/enquiries/mine` — buyer request history or owner enquiries for their own plots
- `GET /api/admin/enquiries`, `PUT /api/enquiries/{id}/visit` — admin enquiry overview and owner site visit management
- `POST /api/support`, `GET /api/admin/support-queries`, `PUT /api/admin/support-queries/{id}/status` — submit and track support messages

Demo enquiry payload:

```json
{"plotId":"TN-CH-102","name":"Demo Buyer","phone":"9876543210","email":"buyer@example.com","requestType":"SITE_VISIT","preferredDate":"2026-11-02","message":"Please call after 5 PM"}
```

The seed listings and contact identities are fictional. Coordinates and map boundaries are illustrative approximations for the demo; confirm title, approval, survey records, price, and exact location independently before any real transaction. New owner listings, user accounts, saved plots, and enquiries persist in PostgreSQL. For deployment, use private credentials, HTTPS, backups, and production-grade secrets.


## Security and data-quality behaviour (added)

- **Listing validation.** `POST/PUT /api/plots` validate every field server-side (length limits, no `<`/`>`, Tamil Nadu coordinate range, https/`/uploads/` image links only, positive price/area). The browser form is not trusted.
- **Contact details come from the account.** The listing's displayed owner name and phone are taken from the signed-in owner's account, never from the request body.
- **Verification label.** Every listing carries a server-set `verification`: `DEMO` (seed data), `SELF_DECLARED` (owner-provided; any edit resets to this) or `ADMIN_REVIEWED` (an admin approved the listing text). None of these certifies title, approvals or boundaries; the UI shows "Owner-declared · not independently verified" beside the approval label.
- **Output escaping.** The browser app escapes all listing text before rendering (previously owner text was injected as raw HTML).
- **Rate limits** (per client IP, in memory): login 10/15 min, register 10/h, enquiries 20/h, support 10/h. Behind a reverse proxy set `FORWARD_HEADERS_STRATEGY=framework`.
- **Headers:** CSP, `X-Frame-Options: DENY`, `nosniff`, referrer policy. Passwords need 8+ characters with a letter and a number.
- **Photos:** file signature (JPEG/PNG/WebP magic bytes) is checked, not just the Content-Type; max 20 photos per listing.
- **No personal defaults.** `ADMIN_EMAIL` and the `DEMO_OWNER_*` settings default to empty. Demo-owner reassignment of seed plots only runs when `DEMO_OWNER_EMAIL` is set.
- **Production profile.** Run with `SPRING_PROFILES_ACTIVE=prod`: requires `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` (no defaults) and sets secure session cookies.

## Tests

- `mvn test` runs the unit tests in `src/test/java` (validator, rate limiter, photo signature check).
- `scripts/smoke_test.py` runs 30 end-to-end checks (accounts, listing review, spoofing attempts, the pre-booking hold flow, rate limiting) against a running instance and a scratch database. See the docstring for usage.
