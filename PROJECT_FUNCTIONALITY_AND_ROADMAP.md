# MannVeedu — Current Functionality and Improvement Report

**Report date:** 29 October 2026  
**Application:** Tamil Nadu land and plot marketplace  
**Current development URL:** `http://localhost:8082/`  
**Configured default port:** `8081`

## 0. Update — 29 October 2026 (hardening pass)

Done in code: server-side listing validation; owner name/phone taken from the account; server-set verification label (`DEMO` / `SELF_DECLARED` / `ADMIN_REVIEWED`) shown in the UI; stored-XSS fix in the listing views; rate limiting on sign-in, sign-up, enquiries and support; security headers; photo signature checks; password strength rule; production profile; personal data removed from config defaults; a fix for owner listing submission failing because boundary points were sent as arrays; 6 unit tests plus a 30-check end-to-end script.

Still open from the roadmap: document upload and admin document review, owner KYC, real boundary data, payment/reservation policy, password reset and email/phone verification, admin role screen, managed file storage and backups, migration tool, pagination.

## 1. Executive summary

MannVeedu is a working development version of a land marketplace. Visitors can explore and compare plots, view locations and plot boundaries, and contact listing owners. Buyers and owners have accounts and dashboards. The app supports owner listing submissions, admin review, site visit requests with two-sided date agreement, and owner-approved pre-booking holds.

The core user flows are implemented, but the current listings and contact details are demo data. Plot approval labels and map boundaries have not been independently verified against official land records. Pre-booking is a time-limited request and hold; there is no payment collection, sale contract, or transfer of ownership.

**Recommended next milestone:** verify owners and listings, securely collect land documents, and show clear verification status before presenting the marketplace as a real property service.

## 2. Technology and data

- **Backend:** Java 17+ with Spring Boot and REST endpoints.
- **Frontend:** HTML, CSS, and JavaScript served by Spring Boot; responsive map and listing views.
- **Database:** PostgreSQL. Application data includes users, plots, saved plots, enquiries/site visits, support queries, notifications, listing photos, and pre-bookings.
- **Authentication:** session-based sign-in; passwords are stored as BCrypt hashes. CSRF protection is enabled for write requests.
- **Photos:** image files are stored in the configured local upload directory.
- **Email:** SMTP can send enquiry, site-visit, support, and pre-booking updates when mail credentials are configured through environment settings. Email is not configured by default in this project.

The project currently starts on port 8081 from its default configuration. The latest development instance has been started on port 8082 so it can run alongside the older server. Use 8082 to see the latest source changes.

## 3. Current functionality

### Public marketplace

- Homepage and plot browsing for residential, agricultural, and commercial land.
- Map pins and illustrative plot outlines for listed plots.
- Plot detail pages with price, area, access, facing, approval label, survey reference, utilities, owner profile, photos, and highlights.
- Filters for location, land type, price, plot size, facing, and approval status.
- Comparison of up to three plots.
- Owner profile view and contact/site-visit request forms.
- Help/query form for visitors who need to contact the service team.
- Reserved plots are checked before a buyer requests a pre-booking; a held plot cannot be pre-booked again.

### Buyer account

- Register and sign in as a buyer; save plots to a shortlist.
- View submitted property enquiries and site-visit requests in the buyer dashboard.
- Request site visits with a preferred date.
- Respond to an owner-proposed visit date by accepting it or proposing another date. The visit becomes confirmed only after both sides agree.
- Cancel active site visits; view completed and cancelled visits in history.
- Request pre-booking for a plot and track whether it is pending, approved, declined, cancelled, or expired.
- Cancel a pending pre-booking or release an approved hold.

### Owner account

- Submit land listings and update or remove active listings.
- Upload listing photos. New and edited listings enter the admin review queue before public approval.
- View enquiries and site visits for the owner’s listings.
- Confirm a buyer’s original requested date, propose a new date, accept a buyer counter-proposal, cancel a visit, or mark a confirmed visit complete.
- Review pre-booking requests, approve or decline them, and release an approved hold.
- Editing or removing a plot is blocked while an approved pre-booking hold is active.

### Admin account

- Review new and edited listings and approve or reject them.
- View submitted enquiries and site visits.
- View support queries and mark them open, in progress, or resolved.
- Admin account provisioning currently requires a database role change; there is no admin-user management screen.

### Pre-booking rules

1. A signed-in buyer can request a pre-booking for an approved, active listing.
2. The same buyer cannot have duplicate pending pre-bookings for the same plot. Different buyers may submit pending requests while the plot remains available.
3. The owner chooses whether to approve or decline each request.
4. Approval places a 48-hour hold on the plot. Other pending requests for that plot are declined when one is approved.
5. The buyer or owner can cancel/release an approved hold. The scheduled expiry releases the plot after 48 hours if no one cancels sooner.
6. The app does not collect a deposit or purchase price. The buyer acknowledges that the owner must approve the request.

## 4. Important current limitations

### Data accuracy and trust

- Seed plots, names, prices, contact details, approvals, and map boundaries are illustrative demo information.
- A displayed approval label is listing information, not proof that an official agency verified the plot.
- Map pins and drawn boundaries are approximate. They are not a substitute for a survey, FMB sketch, or official cadastral boundary.
- There is no owner identity/KYC verification or secure land-document review workflow yet.

### Transaction scope

- Pre-booking is a request plus a temporary owner-approved hold. It is not a purchase, legal agreement, title check, or ownership transfer.
- There is no payment gateway, booking-fee receipt, refund process, e-signature, sale agreement, or conveyance workflow.
- The 48-hour hold length is a current product rule and may need to change based on the intended business policy.

### Operations and security

- SMTP credentials are blank by default, so email delivery requires local environment configuration.
- The app uses a local upload folder; files need backups and are not yet stored in managed object storage.
- Database defaults are for local development only. Production needs private credentials, a least-privilege database account, HTTPS, secure cookie settings, and production error handling.
- There is no self-service password reset, email/phone verification, MFA, or comprehensive abuse/rate-limit protection.
- There is no automated test suite in the project yet. The recent build was compiled with tests skipped; basic app and availability endpoints were checked manually.

## 5. Recommended improvement roadmap

### Priority 1 — Verification and accurate listings

- Add owner verification and clear verification states (for example: submitted, under review, verified, rejected).
- Add protected document uploads for Patta/Chitta, survey/FMB, encumbrance certificate, layout approval, and owner authorization.
- Give admins a document-review screen and an audit trail for decisions.
- Distinguish **owner provided**, **admin reviewed**, and **officially verified** information. Never imply official verification when only an upload was checked.
- Show map boundaries as approximate until checked against reliable survey data; store boundary source and verification status.
- Replace or clearly label demo plots and sample contact details before public launch.

### Priority 2 — Safe transaction and reservation policy

- Decide whether a pre-booking is only an expression of interest or a paid reservation.
- If payment is introduced, specify the fee, payment provider, receipt, cancellation/refund rules, failed-payment handling, and reconciliation before implementation.
- Add a clear reservation policy, buyer/owner confirmation history, and timestamps for every status change.
- Keep purchase contracts and legal transfer separate from a temporary hold; obtain appropriate professional review before making legal claims.

### Priority 3 — Account, security, and operations

- Add verified email/phone, password reset, and secure account recovery.
- Provide an admin screen for account roles instead of manual database edits.
- Move database and SMTP credentials to a deployment secret manager; enable HTTPS-only cookies in production.
- Add rate limits, upload scanning/type validation, access logging, and stronger controls for sensitive land documents.
- Add automated backups and a tested restore procedure for both PostgreSQL and uploaded photos.
- Use a versioned database migration tool for future schema changes.

### Priority 4 — Reliability and user experience

- Add automated tests for account permissions, listing moderation, site-visit transitions, duplicate pre-bookings, concurrent owner approvals, cancellations, and 48-hour expiry.
- Add visible listing availability labels throughout search cards and map popups, not only in plot details.
- Improve buyer/owner notifications with delivery status, retry handling, and optional SMS/WhatsApp only after choosing a provider.
- Add better location search, nearby landmarks, accurate map drawing, and mobile usability/accessibility checks.
- Add pagination and admin reporting as listings and enquiry volumes grow.

## 6. Suggested release checklist

Before inviting real customers, replace demo listing data, verify owner identities and land documents, confirm every plot’s exact location and availability, choose the pre-booking policy, configure private production credentials and HTTPS, set up email delivery, back up the database/uploads, and exercise buyer and owner flows with separate test accounts.

## 7. Main API areas

- `/api/auth/**` — account registration, sign-in, session, and sign-out.
- `/api/plots/**`, `/api/my-plots` — plot search/detail, owner listings, photos, owner profiles, and listing review.
- `/api/saved-plots/**` — buyer shortlist.
- `/api/enquiries/**` — contact requests, site visits, owner updates, and buyer date responses.
- `/api/prebookings/**` — availability, buyer requests, owner decisions, and buyer cancellation.
- `/api/support` and `/api/admin/support-queries/**` — support submissions and admin status handling.
- `/api/admin/**` — listing moderation and enquiry review.

---

This report describes the current development code and does not certify land ownership, title, price, boundary, regulatory approval, or legal readiness.
