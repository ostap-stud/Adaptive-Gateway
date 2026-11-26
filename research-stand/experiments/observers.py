"""Loopback-only instruments on the control plane. Business probes go directly to Gateway."""
from collections import deque
from hashlib import sha256
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from threading import Lock, Thread
from time import perf_counter_ns

DOC = "/catalog-service/v3/api-docs"
PATH = "/catalog-service/selection"
PORT = dict(eureka=38761, auth=38334, gateway=38088, manager=38335, catalog=38911,
            inventory=38912, order=38913, registry=38762, docs=38914, sync=38336)
BASE = {k: f"http://127.0.0.1:{v}" for k, v in PORT.items()}
now = perf_counter_ns


class Instruments:
    def __init__(self):
        self.documents = {}
        self.profile = "default"
        self.registry_off = False
        self.lock = Lock()
        self.events = deque()
        self.servers = []

    def event(self, kind, **fields):
        with self.lock:
            self.events.append(dict(kind=kind, **fields))

    def since(self, t, kind):
        with self.lock:
            return [e.copy() for e in self.events if e["kind"] == kind and e["start_ns"] >= t]

    def publish(self, profile):
        with self.lock:
            self.profile = profile
            return now()

    def serve(self, kind, method, path, headers, body):
        start = now()
        if kind == "docs":
            if method != "GET" or path != DOC:
                return 404, b""
            with self.lock:
                profile = self.profile
                data = self.documents.get(profile)
                if not headers.get("X-Experiment-Confirmation"):
                    self.events.append(dict(kind="fetch", start_ns=start, profile=profile, status=200 if data else 503))
            return (200, data) if data else (503, b"{}")
        if kind == "registry":
            if method != "GET" or path.rstrip("/") != "/eureka/apps":
                return 404, b""
            self.event("cycle", start_ns=start, unavailable=self.registry_off)
            if self.registry_off:
                return 503, b"{}"
        elif not path.startswith("/auth-service/internal/adaptive/services/"):
            return 404, b""
        dest = "eureka" if kind == "registry" else "auth"
        conn = HTTPConnection("127.0.0.1", PORT[dest], timeout=12)
        try:
            outgoing = {"Accept":"application/json", "Content-Type":"application/json"}
            if kind == "sync" and headers.get("X-Adaptive-Key"):
                outgoing["X-Adaptive-Key"] = headers["X-Adaptive-Key"]
            conn.request(method, path, body or None, outgoing)
            res = conn.getresponse()
            status, data = res.status, res.read()
        finally:
            conn.close()
        if kind == "registry" and status == 200:
            registry = json.loads(data)
            apps = registry["applications"].get("application", [])
            for app in apps if isinstance(apps, list) else [apps]:
                if app["name"].lower() == "catalog-service":
                    items = app["instance"]
                    for instance in items if isinstance(items, list) else [items]:
                        instance["port"]["$"] = PORT["docs"]
            data = json.dumps(registry).encode()
        if kind == "sync" and method != "GET":
            result = json.loads(data or b"{}")
            self.event("sync", start_ns=start, end_ns=now(), method=method, path=path,
                       http=status, status=result.get("status"), version=result.get("configurationVersion"),
                       request_id=result.get("requestId"))
        return status, data

    def start(self):
        instruments = self
        class Handler(BaseHTTPRequestHandler):
            def dispatch(self):
                body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
                try:
                    status, data = instruments.serve(self.server.kind, self.command, self.path, self.headers, body)
                except (OSError, ValueError, KeyError) as exc:
                    instruments.event("instrument_error", start_ns=now(), detail=str(exc))
                    status, data = 502, b"{}"
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            do_GET = do_PUT = do_POST = dispatch
            def log_message(self, *_):
                pass

        for kind in ("docs", "registry", "sync"):
            server = ThreadingHTTPServer(("127.0.0.1", PORT[kind]), Handler)
            server.kind = kind
            self.servers.append(server)
            Thread(target=server.serve_forever, daemon=True).start()

    def close(self):
        for server in self.servers:
            server.shutdown()
            server.server_close()


def digest(data):
    return sha256(data).hexdigest()
