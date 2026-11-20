"""Exercise the real PowerShell readiness probe against a loopback HTTP fixture."""
import json
import os
from pathlib import Path
import subprocess
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


ROOT = Path(__file__).resolve().parents[2]
PROBE = ROOT / "scripts" / "check-knowledge-ready.ps1"
POWERSHELL = Path(os.environ["SystemRoot"]) / "System32/WindowsPowerShell/v1.0/powershell.exe"


class ReadinessTests(unittest.TestCase):
    def run_probe(self, status, payload):
        calls = []

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def reply(self, code, body):
                data = json.dumps(body, ensure_ascii=False).encode("utf-8")
                self.send_response(code)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def do_GET(self):
                calls.append((self.path, None))
                self.reply(200, {"status": "UP"})

            def do_POST(self):
                body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                calls.append((self.path, body))
                self.reply(status, payload)

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            result = subprocess.run(
                [str(POWERSHELL), "-NoProfile", "-File", str(PROBE),
                 "-BridgeBaseUrl", f"http://127.0.0.1:{server.server_port}", "-TimeoutSeconds", "3"],
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=15,
            )
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)
        return result, calls

    def test_success_requires_real_graph_evidence_in_the_fixed_space(self):
        result, calls = self.run_probe(200, {
            "hasEvidence": True,
            "triples": [{"subject": "需求分析", "predicate": "前置于", "object": "系统设计", "score": 0.8}],
        })
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual([path for path, _ in calls], ["/health", "/api/knowledge/retrieve"])
        self.assertEqual(calls[1][1]["spaceId"], 1)
        self.assertEqual(calls[1][1]["entities"], ["需求分析"])

    def test_health_up_is_not_ready_when_retrieval_returns_503(self):
        result, calls = self.run_probe(503, {"detail": "Unknown client type"})
        self.assertEqual([path for path, _ in calls], ["/health", "/api/knowledge/retrieve"])
        self.assertNotEqual(result.returncode, 0)

    def test_empty_evidence_does_not_report_ready(self):
        result, calls = self.run_probe(200, {"hasEvidence": False, "triples": []})
        self.assertEqual(len(calls), 2)
        self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
