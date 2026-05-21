#!/usr/bin/env python3
"""
Reset and seed IssueFlow with realistic demo data for frontend testing.

The script intentionally creates data through the HTTP API after the database
cleanup, so audit logs, auth, auto-assignment, mentions, dependencies, and soft
delete behavior look like real application usage.

Usage:
  python3 scripts/seed_realistic_demo.py
  python3 scripts/seed_realistic_demo.py --base-url http://localhost:8080
  python3 scripts/seed_realistic_demo.py --skip-reset
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any


JsonObject = dict[str, Any]


@dataclass
class Response:
    status: int
    text: str

    @property
    def json(self) -> Any:
        if not self.text:
            return None
        return json.loads(self.text)


class IssueFlowSeeder:
    def __init__(self, base_url: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.token: str | None = None
        self.users: dict[str, JsonObject] = {}
        self.projects: dict[str, JsonObject] = {}
        self.tickets: dict[str, JsonObject] = {}
        self.comments: dict[str, JsonObject] = {}

    def request(
        self,
        method: str,
        path: str,
        *,
        body: JsonObject | None = None,
        token: bool = True,
        headers: dict[str, str] | None = None,
        data: bytes | None = None,
    ) -> Response:
        request_headers = dict(headers or {})
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        if token and self.token:
            request_headers["Authorization"] = f"Bearer {self.token}"

        req = urllib.request.Request(
            self.base_url + path,
            data=data,
            method=method,
            headers=request_headers,
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as res:
                return Response(res.status, res.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            text = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {path} failed with HTTP {exc.code}: {text}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"Could not reach {self.base_url}: {exc}") from exc

    def assert_ok(self, response: Response, action: str) -> Any:
        if response.status != 200:
            raise RuntimeError(f"{action} failed with HTTP {response.status}: {response.text}")
        return response.json

    def create_user(self, key: str, username: str, email: str, full_name: str, role: str) -> JsonObject:
        user = self.assert_ok(
            self.request(
                "POST",
                "/users",
                token=False,
                body={
                    "username": username,
                    "email": email,
                    "fullName": full_name,
                    "role": role,
                },
            ),
            f"create user {username}",
        )
        self.users[key] = user
        print(f"  user     {username:<18} id={user['id']}")
        return user

    def login(self, username: str, password: str = "secret") -> None:
        body = self.assert_ok(
            self.request(
                "POST",
                "/auth/login",
                token=False,
                body={"username": username, "password": password},
            ),
            f"login {username}",
        )
        self.token = body["accessToken"]
        print(f"  login    {username}")

    def create_project(self, key: str, name: str, description: str, owner_key: str) -> JsonObject:
        project = self.assert_ok(
            self.request(
                "POST",
                "/projects",
                body={
                    "name": name,
                    "description": description,
                    "ownerId": self.users[owner_key]["id"],
                },
            ),
            f"create project {name}",
        )
        self.projects[key] = project
        print(f"  project  {name:<42} id={project['id']}")
        return project

    def create_ticket(
        self,
        key: str,
        title: str,
        description: str,
        status: str,
        priority: str,
        ticket_type: str,
        project_key: str,
        assignee_key: str | None,
        due_in_days: int | None = None,
    ) -> JsonObject:
        body: JsonObject = {
            "title": title,
            "description": description,
            "status": status,
            "priority": priority,
            "type": ticket_type,
            "projectId": self.projects[project_key]["id"],
        }
        if assignee_key is not None:
            body["assigneeId"] = self.users[assignee_key]["id"]
        if due_in_days is not None:
            due_at = datetime.now(timezone.utc) + timedelta(days=due_in_days)
            body["dueDate"] = due_at.replace(microsecond=0).isoformat().replace("+00:00", "Z")

        ticket = self.assert_ok(
            self.request("POST", "/tickets", body=body),
            f"create ticket {title}",
        )
        self.tickets[key] = ticket
        print(f"  ticket   {title:<56} id={ticket['id']}")
        return ticket

    def patch_ticket(self, ticket_key: str, body: JsonObject, label: str) -> None:
        ticket_id = self.tickets[ticket_key]["id"]
        self.assert_ok(self.request("PATCH", f"/tickets/{ticket_id}", body=body), label)

    def add_dependency(self, ticket_key: str, blocker_key: str) -> None:
        ticket_id = self.tickets[ticket_key]["id"]
        blocker_id = self.tickets[blocker_key]["id"]
        self.assert_ok(
            self.request(
                "POST",
                f"/tickets/{ticket_id}/dependencies",
                body={"blockedBy": blocker_id},
            ),
            f"add dependency {ticket_key}->{blocker_key}",
        )
        print(f"  depends  ticket {ticket_id} blocked by {blocker_id}")

    def add_comment(self, key: str, ticket_key: str, author_key: str, content: str) -> JsonObject:
        ticket_id = self.tickets[ticket_key]["id"]
        comment = self.assert_ok(
            self.request(
                "POST",
                f"/tickets/{ticket_id}/comments",
                body={"authorId": self.users[author_key]["id"], "content": content},
            ),
            f"add comment {key}",
        )
        self.comments[key] = comment
        print(f"  comment  ticket {ticket_id:<4} by {self.users[author_key]['username']}")
        return comment

    def upload_text_attachment(self, ticket_key: str, filename: str, content: str) -> None:
        boundary = "issueflow-demo-boundary"
        multipart = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
            "Content-Type: text/plain\r\n\r\n"
            f"{content}\r\n"
            f"--{boundary}--\r\n"
        ).encode("utf-8")
        ticket_id = self.tickets[ticket_key]["id"]
        response = self.request(
            "POST",
            f"/tickets/{ticket_id}/attachments",
            data=multipart,
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        )
        self.assert_ok(response, f"upload attachment {filename}")
        print(f"  attach   {filename:<18} ticket={ticket_id}")

    def delete_ticket(self, ticket_key: str) -> None:
        ticket_id = self.tickets[ticket_key]["id"]
        self.assert_ok(self.request("DELETE", f"/tickets/{ticket_id}"), f"delete ticket {ticket_id}")
        print(f"  deleted  ticket {ticket_id}")

    def delete_project(self, project_key: str) -> None:
        project_id = self.projects[project_key]["id"]
        self.assert_ok(self.request("DELETE", f"/projects/{project_id}"), f"delete project {project_id}")
        print(f"  deleted  project {project_id}")

    def run(self) -> None:
        print("Creating users")
        self.create_user("clara", "clara_admin", "clara.admin@demo.issueflow.test", "Clara Reynolds", "ADMIN")
        self.create_user("avery", "avery_ops", "avery.ops@demo.issueflow.test", "Avery Morgan", "ADMIN")
        self.create_user("noah", "noah_backend", "noah.backend@demo.issueflow.test", "Noah Patel", "DEVELOPER")
        self.create_user("mia", "mia_frontend", "mia.frontend@demo.issueflow.test", "Mia Chen", "DEVELOPER")
        self.create_user("ethan", "ethan_qa", "ethan.qa@demo.issueflow.test", "Ethan Brooks", "DEVELOPER")
        self.create_user("zoe", "zoe_platform", "zoe.platform@demo.issueflow.test", "Zoe Haddad", "DEVELOPER")

        print("\nAuthenticating")
        self.login("clara_admin")

        print("\nCreating projects")
        self.create_project(
            "field",
            "Fiber Field Service Portal",
            "Dispatch, routing, and technician workflow for fiber installation teams.",
            "clara",
        )
        self.create_project(
            "billing",
            "Billing Modernization",
            "Modern customer billing flows, invoice search, and payment reconciliation.",
            "avery",
        )
        self.create_project(
            "retired",
            "Legacy WAP Retirement",
            "Archived cleanup project kept deleted so admin restore screens have real data.",
            "avery",
        )

        print("\nCreating tickets")
        self.create_ticket(
            "gateway",
            "Stabilize API gateway timeout spike",
            "Gateway timeouts are affecting ticket search during peak field dispatch hours.",
            "IN_PROGRESS",
            "CRITICAL",
            "BUG",
            "field",
            "zoe",
            due_in_days=1,
        )
        self.create_ticket(
            "blocked_release",
            "Release technician map clustering",
            "Frontend map clustering is ready but must wait for the gateway timeout fix.",
            "IN_REVIEW",
            "HIGH",
            "FEATURE",
            "field",
            "mia",
            due_in_days=3,
        )
        self.create_ticket(
            "auto_assign",
            "Triage failed photo upload from mobile app",
            "Created without assignee to demonstrate least-loaded developer auto-assignment.",
            "TODO",
            "MEDIUM",
            "BUG",
            "field",
            None,
            due_in_days=5,
        )
        self.create_ticket(
            "qa_regression",
            "Run regression pack for contractor onboarding",
            "Validate contractor onboarding flows after the latest routing changes.",
            "TODO",
            "LOW",
            "TECHNICAL",
            "field",
            "ethan",
            due_in_days=7,
        )
        self.create_ticket(
            "billing_search",
            "Add invoice search by account alias",
            "Support agents need invoice lookup by account alias and recent payment reference.",
            "IN_PROGRESS",
            "HIGH",
            "FEATURE",
            "billing",
            "noah",
            due_in_days=4,
        )
        self.create_ticket(
            "billing_export",
            "Fix CSV export timezone labels",
            "Billing export currently shows UTC timestamps without an explicit timezone label.",
            "TODO",
            "MEDIUM",
            "BUG",
            "billing",
            "mia",
            due_in_days=6,
        )
        self.create_ticket(
            "done_smoke",
            "Complete smoke tests for payment reconciliation",
            "Smoke test checklist completed for the payment reconciliation release candidate.",
            "TODO",
            "LOW",
            "TECHNICAL",
            "billing",
            "ethan",
            due_in_days=-2,
        )
        self.patch_ticket("done_smoke", {"status": "IN_PROGRESS"}, "move smoke ticket to IN_PROGRESS")
        self.patch_ticket("done_smoke", {"status": "IN_REVIEW"}, "move smoke ticket to IN_REVIEW")
        self.patch_ticket("done_smoke", {"status": "DONE"}, "move smoke ticket to DONE")

        self.create_ticket(
            "deleted_ticket",
            "Remove obsolete SOAP adapter notes",
            "Kept soft-deleted so admin deleted-ticket screens have realistic content.",
            "TODO",
            "LOW",
            "TECHNICAL",
            "billing",
            "zoe",
            due_in_days=10,
        )

        print("\nAdding dependencies")
        self.add_dependency("blocked_release", "gateway")
        self.add_dependency("billing_export", "billing_search")

        print("\nAdding comments and mentions")
        self.add_comment(
            "gateway_status",
            "gateway",
            "clara",
            "Please sync with @zoe_platform and @noah_backend before the evening deployment window.",
        )
        self.add_comment(
            "map_review",
            "blocked_release",
            "mia",
            "@ethan_qa the clustering branch is ready for regression once the gateway fix lands.",
        )
        self.add_comment(
            "billing_context",
            "billing_search",
            "avery",
            "@mia_frontend please align the alias search UI with the support-console wording.",
        )
        self.add_comment(
            "export_context",
            "billing_export",
            "noah",
            "Root cause appears to be formatter defaults. @zoe_platform can you confirm UTC handling?",
        )

        print("\nAdding sample attachment")
        self.upload_text_attachment(
            "gateway",
            "incident-notes.txt",
            "Incident bridge notes: monitor gateway latency, retry queue depth, and database pool usage.",
        )

        print("\nCreating soft-deleted demo records")
        self.delete_ticket("deleted_ticket")
        self.delete_project("retired")

        print("\nDemo login credentials")
        print("  username: clara_admin")
        print("  password: secret")
        print("  developer examples: noah_backend / secret, mia_frontend / secret")


def reset_database(container: str, database: str, user: str) -> None:
    sql = """
    TRUNCATE TABLE
      attachments,
      comment_mentions,
      comments,
      ticket_dependencies,
      tickets,
      projects,
      audit_logs,
      users
    RESTART IDENTITY CASCADE;
    """
    command = [
        "docker",
        "exec",
        container,
        "psql",
        "-U",
        user,
        "-d",
        database,
        "-v",
        "ON_ERROR_STOP=1",
        "-c",
        sql,
    ]
    print("Resetting database")
    subprocess.run(command, check=True)


def wait_for_server(base_url: str, timeout_seconds: int) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            urllib.request.urlopen(base_url.rstrip("/") + "/users", timeout=3).close()
            return
        except urllib.error.HTTPError:
            return
        except urllib.error.URLError:
            time.sleep(1)
    raise RuntimeError(f"Server did not respond at {base_url} within {timeout_seconds}s")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Seed IssueFlow with realistic demo data.")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--skip-reset", action="store_true", help="Do not truncate existing app data first.")
    parser.add_argument("--docker-container", default="issueflow-master-db-1")
    parser.add_argument("--database", default="issueflow")
    parser.add_argument("--db-user", default="issueflow")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        if not args.skip_reset:
            reset_database(args.docker_container, args.database, args.db_user)
        wait_for_server(args.base_url, timeout_seconds=20)
        IssueFlowSeeder(args.base_url).run()
    except subprocess.CalledProcessError as exc:
        print(f"\nSeed failed while running: {' '.join(exc.cmd)}", file=sys.stderr)
        return exc.returncode or 1
    except Exception as exc:  # noqa: BLE001 - command-line diagnostics
        print(f"\nSeed failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
