#!/usr/bin/env python3
"""End-to-end checks against a running MannVeedu instance (default http://localhost:8081).

Needs `psql` and PG* env vars (PGHOST/PGUSER/PGPASSWORD/PGDATABASE) only to promote one account to ADMIN.
Run it against a scratch database: it creates accounts and listings. The rate limiter allows 10
registrations per hour per IP, so restart the app between repeated runs.

    BASE=http://localhost:8091 PGHOST=localhost PGUSER=postgres PGPASSWORD=1234 PGDATABASE=mannveedu python3 scripts/smoke_test.py
"""
import json, os, subprocess, sys, time, urllib.error, urllib.request, http.cookiejar

BASE = os.environ.get("BASE", "http://localhost:8081")
RUN = str(int(time.time()))[-6:]
failures = []


class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def call(self, method, path, body=None):
        headers = {"Content-Type": "application/json"}
        token = next((c.value for c in self.jar if c.name == "XSRF-TOKEN"), None)
        if token:
            headers["X-XSRF-TOKEN"] = token
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with self.op.open(req) as r:
                raw = r.read()
                return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read()
            try:
                return e.code, json.loads(raw)
            except Exception:
                return e.code, raw.decode(errors="replace")

    def start(self):
        self.call("GET", "/api/auth/csrf")
        return self


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"))
    if not cond:
        failures.append(name)


def register(role, tag):
    c = Client().start()
    email = f"{tag}{RUN}@example.com"
    status, body = c.call("POST", "/api/auth/register", {
        "fullName": f"{tag.title()} Real Name", "email": email, "phone": "+91 90000 1" + RUN[-4:],
        "password": "Passw0rd" + RUN, "role": role})
    assert status == 201, (status, body)
    c.start()
    return c, email


def plot(pid, **over):
    p = {"id": pid, "title": "Quiet corner plot near the coast", "type": "Residential", "city": "Chennai",
         "district": "Chengalpattu", "locality": "Uthandi", "address": "ECR, Chennai", "latitude": 12.87,
         "longitude": 80.24, "cents": 8, "acres": 0.08, "price": 6400000, "pricePerCent": 800000,
         "roadWidth": "30 ft", "facing": "East", "zoning": "Residential", "approved": True,
         "approvalLabel": "CMDA approved", "patta": "Available", "surveyNo": "Survey 1", "electricity": True,
         "water": True, "fenced": True, "owner": "Somebody Else", "ownerRole": "Owner", "phone": "+91 11111 11111",
         "description": "A calm plot.", "image": "https://images.example.com/a.jpg?w=1000",
         "highlights": ["Corner site"], "boundary": [[12.871, 80.239], [12.871, 80.241], [12.869, 80.241]],
         "verification": "ADMIN_REVIEWED"}
    p.update(over)
    return p


# ---- accounts ---------------------------------------------------------------------------------
owner, owner_email = register("OWNER", "owner")
owner2, _ = register("OWNER", "ownertwo")
buyer_a, _ = register("BUYER", "buyera")
buyer_b, _ = register("BUYER", "buyerb")
admin, admin_email = register("OWNER", "admin")
subprocess.run(["psql", "-qtc", f"UPDATE users SET role='ADMIN' WHERE email='{admin_email}'"], check=True)
admin.call("POST", "/api/auth/logout")
admin.start()
status, _ = admin.call("POST", "/api/auth/login", {"email": admin_email, "password": "Passw0rd" + RUN})
admin.start()
check("admin login", status == 200, status)

status, body = Client().start().call("POST", "/api/auth/register", {
    "fullName": "Weak", "email": f"weak{RUN}@example.com", "password": "abcdefgh", "role": "BUYER"})
check("password without a number rejected", status == 400, (status, body))

# ---- listing validation ---------------------------------------------------------------------------
PID = "TN-T-" + RUN
status, body = buyer_a.call("POST", "/api/plots", plot(PID))
check("buyer cannot create listing", status == 403, status)

status, body = owner.call("POST", "/api/plots", plot("TN-X-" + RUN, title="<img src=x onerror=alert(1)>"))
check("markup in title rejected (stored XSS)", status == 400, (status, body))
status, body = owner.call("POST", "/api/plots", plot("TN-X2-" + RUN, image="javascript:alert(1)"))
check("javascript: image rejected", status == 400, (status, body))
status, body = owner.call("POST", "/api/plots", plot("TN-X3-" + RUN, latitude=51.5, longitude=-0.12))
check("coordinates outside Tamil Nadu rejected", status == 400, (status, body))
status, body = owner.call("POST", "/api/plots", plot("TN-X4-" + RUN, price=-5))
check("negative price rejected", status == 400, (status, body))

status, body = owner.call("POST", "/api/plots", plot(PID))
check("owner creates listing", status == 201, (status, body))
check("contact name comes from the account, not the request", body.get("owner") == "Owner Real Name", body.get("owner"))
check("contact phone comes from the account", body.get("phone", "").startswith("+91 90000 1"), body.get("phone"))
check("client cannot self-assign ADMIN_REVIEWED", body.get("verification") == "SELF_DECLARED", body.get("verification"))

status, _ = Client().start().call("GET", f"/api/plots/{PID}")
check("pending listing is not public", status == 404, status)
status, body = admin.call("PUT", f"/api/admin/listings/{PID}/review", {"status": "APPROVED"})
check("admin approves listing", status in (200, 204), (status, body))
status, body = Client().start().call("GET", f"/api/plots/{PID}")
check("approved listing is public and marked ADMIN_REVIEWED", status == 200 and body.get("verification") == "ADMIN_REVIEWED", (status, body))

status, body = owner.call("PUT", f"/api/plots/{PID}", plot(PID, price=6500000))
check("owner edit accepted", status == 200, (status, body))
status, _ = Client().start().call("GET", f"/api/plots/{PID}")
check("edited listing returns to review (hidden again)", status == 404, status)
admin.call("PUT", f"/api/admin/listings/{PID}/review", {"status": "APPROVED"})

status, body = owner2.call("DELETE", f"/api/plots/{PID}")
check("other owner cannot delete the listing", status == 404, (status, body))

# ---- pre-booking flow -------------------------------------------------------------------------------
status, pa = buyer_a.call("POST", "/api/prebookings", {"plotId": PID, "message": "Interested"})
check("buyer A requests pre-booking", status == 201, (status, pa))
status, pb = buyer_b.call("POST", "/api/prebookings", {"plotId": PID, "message": "Me too"})
check("buyer B can also queue a request", status == 201, (status, pb))
status, _ = buyer_a.call("POST", "/api/prebookings", {"plotId": PID})
check("duplicate pending request from the same buyer rejected", status == 409, status)
status, _ = owner2.call("PUT", f"/api/prebookings/{pa['id']}/owner", {"status": "APPROVED"})
check("unrelated owner cannot decide", status == 404, status)
status, body = owner.call("PUT", f"/api/prebookings/{pa['id']}/owner", {"status": "APPROVED"})
check("owner approves buyer A (48h hold)", status == 200 and body["status"] == "APPROVED", (status, body))
status, body = Client().start().call("GET", f"/api/prebookings/availability/{PID}")
check("plot shows RESERVED", body.get("status") == "RESERVED", body)
_, mine = buyer_b.call("GET", "/api/prebookings/mine")
check("competing request was declined", mine and mine[0]["status"] == "DECLINED", mine)
status, _ = owner.call("PUT", f"/api/plots/{PID}", plot(PID))
check("editing a reserved listing blocked", status == 409, status)
status, _ = owner.call("PUT", f"/api/prebookings/{pa['id']}/owner", {"status": "CANCELLED"})
check("owner releases hold", status == 200, status)
status, body = Client().start().call("GET", f"/api/prebookings/availability/{PID}")
check("plot AVAILABLE again", body.get("status") == "AVAILABLE", body)

# ---- headers & rate limiting (keep last: it exhausts the login budget for this IP) ---------------------
c = Client()
req = urllib.request.Request(BASE + "/")
with urllib.request.urlopen(req) as r:
    h = r.headers
check("CSP header present", "frame-ancestors 'none'" in (h.get("Content-Security-Policy") or ""))
check("clickjacking protection", h.get("X-Frame-Options") == "DENY")
codes = [Client().start().call("POST", "/api/auth/login", {"email": "nobody@example.com", "password": "wrongpass1"})[0] for _ in range(14)]
check("login attempts are rate limited (429)", 429 in codes and codes[0] == 401, codes)

print()
print("FAILED: " + ", ".join(failures) if failures else "All checks passed")
sys.exit(1 if failures else 0)
