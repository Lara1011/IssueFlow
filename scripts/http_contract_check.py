#!/usr/bin/env python3
"""
IssueFlow HTTP contract checker.

Runs black-box API requests against a running IssueFlow server and prints
expected vs actual results for both allowed and not-allowed behavior from the
README/PDF contract.

Usage:
  python3 scripts/http_contract_check.py
  python3 scripts/http_contract_check.py --base-url http://localhost:8080
  python3 scripts/http_contract_check.py --include-scheduler
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import os
import random
import string
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any, Callable


Json = dict[str, Any] | list[Any] | str | int | float | bool | None
Validator = Callable[["Response"], tuple[bool, str]]


@dataclass
class Response:
    status: int
    headers: dict[str, str]
    text: str

    @property
    def json(self) -> Json:
        if not self.text:
            return None
        return json.loads(self.text)


class ContractRunner:
    def __init__(self, base_url: str, prefix: str, verbose_body: bool) -> None:
        self.base_url = base_url.rstrip("/")
        self.prefix = prefix
        self.verbose_body = verbose_body
        self.tokens: dict[str, str] = {}
        self.ids: dict[str, int] = {}
        self.total = 0
        self.passed = 0
        self.failed = 0
        self.skipped = 0
        self.current_section = ""

    def section(self, name: str) -> None:
        self.current_section = name
        print(f"\n=== {name} ===")

    def skip(self, name: str, reason: str) -> None:
        self.skipped += 1
        print(f"[SKIP] {name}")
        print(f"       Reason: {reason}")

    def request(
        self,
        method: str,
        path: str,
        *,
        json_body: dict[str, Any] | None = None,
        data: bytes | None = None,
        content_type: str | None = None,
        token: str | None = "admin",
        headers: dict[str, str] | None = None,
    ) -> Response:
        url = self.base_url + path
        request_headers = dict(headers or {})

        if json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        elif content_type is not None:
            request_headers["Content-Type"] = content_type

        if token and token in self.tokens:
            request_headers["Authorization"] = f"Bearer {self.tokens[token]}"

        req = urllib.request.Request(url, data=data, method=method, headers=request_headers)
        try:
            with urllib.request.urlopen(req, timeout=30) as res:
                body = res.read().decode("utf-8", errors="replace")
                return Response(res.status, dict(res.headers.items()), body)
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            return Response(exc.code, dict(exc.headers.items()), body)
        except urllib.error.URLError as exc:
            if isinstance(exc.reason, (BrokenPipeError, ConnectionResetError)):
                return Response(0, {}, "Connection closed while the server rejected the upload.")
            raise RuntimeError(f"Could not reach {url}: {exc}") from exc

    def check(
        self,
        name: str,
        method: str,
        path: str,
        expected_status: int | set[int],
        *,
        json_body: dict[str, Any] | None = None,
        data: bytes | None = None,
        content_type: str | None = None,
        token: str | None = "admin",
        headers: dict[str, str] | None = None,
        validator: Validator | None = None,
        capture: Callable[[Response], None] | None = None,
    ) -> Response:
        self.total += 1
        expected = expected_status if isinstance(expected_status, set) else {expected_status}
        response = self.request(
            method,
            path,
            json_body=json_body,
            data=data,
            content_type=content_type,
            token=token,
            headers=headers,
        )

        status_ok = response.status in expected
        body_ok = True
        body_message = "body not checked"
        if validator is not None:
            try:
                body_ok, body_message = validator(response)
            except Exception as exc:  # noqa: BLE001 - diagnostic runner
                body_ok, body_message = False, f"validator crashed: {type(exc).__name__}: {exc}"

        passed = status_ok and body_ok
        if passed:
            self.passed += 1
            outcome = "PASS"
        else:
            self.failed += 1
            outcome = "FAIL"

        expected_text = ", ".join(str(item) for item in sorted(expected))
        print(f"[{outcome}] {name}")
        print(f"       {method} {path}")
        print(f"       Expected: HTTP {expected_text}; {body_message}")
        print(f"       Actual:   HTTP {response.status}; {self.body_summary(response)}")

        if capture and passed:
            capture(response)
        return response

    def body_summary(self, response: Response) -> str:
        if not response.text:
            return "<empty body>"
        text = response.text.strip()
        if not self.verbose_body and len(text) > 360:
            text = text[:357] + "..."
        return text.replace("\n", "\\n")

    def summary(self) -> int:
        print("\n=== Summary ===")
        print(f"Base URL: {self.base_url}")
        print(f"Data prefix: {self.prefix}")
        print(f"Passed: {self.passed}")
        print(f"Failed: {self.failed}")
        print(f"Skipped: {self.skipped}")
        print(f"Total checked: {self.total}")
        return 0 if self.failed == 0 else 1


def has_json_object(*fields: str) -> Validator:
    def validate(response: Response) -> tuple[bool, str]:
        body = response.json
        if not isinstance(body, dict):
            return False, "expected JSON object"
        missing = [field for field in fields if field not in body]
        if missing:
            return False, f"missing fields: {', '.join(missing)}"
        return True, f"body has fields: {', '.join(fields)}"

    return validate


def has_json_array(response: Response) -> tuple[bool, str]:
    return (isinstance(response.json, list), "expected JSON array")


def empty_or_json(response: Response) -> tuple[bool, str]:
    if not response.text.strip():
        return True, "empty body is allowed"
    try:
        response.json
        return True, "JSON body is allowed"
    except json.JSONDecodeError:
        return False, "expected empty body or JSON"


def error_json(response: Response) -> tuple[bool, str]:
    try:
        body = response.json
    except json.JSONDecodeError:
        return False, "expected JSON error body"
    if not isinstance(body, dict):
        return False, "expected JSON error object"
    required = {"status", "error", "message", "path"}
    if not required.issubset(body):
        return False, f"expected error fields: {', '.join(sorted(required))}"
    return True, "body is structured error JSON"


def csv_export(response: Response) -> tuple[bool, str]:
    reader = csv.reader(io.StringIO(response.text))
    rows = list(reader)
    expected_header = ["id", "title", "description", "status", "priority", "type", "assigneeId"]
    if not rows:
        return False, "expected CSV content"
    if rows[0] != expected_header:
        return False, f"expected CSV header {expected_header}"
    return True, "CSV header matches contract"


def import_summary(response: Response) -> tuple[bool, str]:
    body = response.json
    if not isinstance(body, dict):
        return False, "expected JSON object"
    if not {"created", "failed", "errors"}.issubset(body):
        return False, "expected created, failed, errors"
    if body["created"] < 1 or body["failed"] < 1:
        return False, "expected at least one imported row and one failed row"
    return True, "import summary has created, failed, errors"


def mentions_include(user_id: int) -> Validator:
    def validate(response: Response) -> tuple[bool, str]:
        body = response.json
        if not isinstance(body, dict) or "data" not in body:
            return False, "expected paged mentions object with data"
        data = body.get("data")
        if not isinstance(data, list):
            return False, "expected data array"
        for comment in data:
            for mentioned in comment.get("mentionedUsers", []):
                if mentioned.get("id") == user_id:
                    return True, "mentions include expected user"
        return False, "expected mentioned user was not found"

    return validate


def upload_rejected(response: Response) -> tuple[bool, str]:
    if response.status == 0:
        return True, "server closed the upload connection while rejecting the oversized file"
    if not response.text.strip():
        return True, "empty rejection body is allowed"
    return error_json(response)


def field_equals(field: str, expected_value: Any) -> Validator:
    def validate(response: Response) -> tuple[bool, str]:
        body = response.json
        if not isinstance(body, dict):
            return False, "expected JSON object"
        actual_value = body.get(field)
        return actual_value == expected_value, f"expected {field}={expected_value!r}"

    return validate


def list_contains_id(expected_id: int) -> Validator:
    def validate(response: Response) -> tuple[bool, str]:
        body = response.json
        if not isinstance(body, list):
            return False, "expected JSON array"
        if any(item.get("id") == expected_id for item in body if isinstance(item, dict)):
            return True, f"array contains id {expected_id}"
        return False, f"array does not contain id {expected_id}"

    return validate


def list_not_contains_id(forbidden_id: int) -> Validator:
    def validate(response: Response) -> tuple[bool, str]:
        body = response.json
        if not isinstance(body, list):
            return False, "expected JSON array"
        if any(item.get("id") == forbidden_id for item in body if isinstance(item, dict)):
            return False, f"array should not contain id {forbidden_id}"
        return True, f"array does not contain id {forbidden_id}"

    return validate


def multipart(
    fields: dict[str, str],
    files: dict[str, tuple[str, str, bytes]],
) -> tuple[bytes, str]:
    boundary = "IssueFlowBoundary" + "".join(random.choice(string.ascii_letters) for _ in range(18))
    parts: list[bytes] = []

    for name, value in fields.items():
        parts.append(f"--{boundary}\r\n".encode())
        parts.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        parts.append(value.encode())
        parts.append(b"\r\n")

    for name, (filename, content_type, content) in files.items():
        parts.append(f"--{boundary}\r\n".encode())
        parts.append(
            (
                f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                f"Content-Type: {content_type}\r\n\r\n"
            ).encode()
        )
        parts.append(content)
        parts.append(b"\r\n")

    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def iso_future(days: int) -> str:
    return (datetime.now(timezone.utc) + timedelta(days=days)).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def iso_past(days: int) -> str:
    return (datetime.now(timezone.utc) - timedelta(days=days)).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def capture_id(runner: ContractRunner, key: str) -> Callable[[Response], None]:
    def capture(response: Response) -> None:
        body = response.json
        if isinstance(body, dict) and isinstance(body.get("id"), int):
            runner.ids[key] = body["id"]

    return capture


def capture_token(runner: ContractRunner, key: str) -> Callable[[Response], None]:
    def capture(response: Response) -> None:
        body = response.json
        if isinstance(body, dict) and isinstance(body.get("accessToken"), str):
            runner.tokens[key] = body["accessToken"]

    return capture


def create_user(r: ContractRunner, key: str, role: str, *, token: str | None = None) -> str:
    username = f"{r.prefix}_{key}"
    r.check(
        f"Create {role.lower()} user {key}",
        "POST",
        "/users",
        200,
        token=token,
        json_body={
            "username": username,
            "email": f"{username}@example.com",
            "fullName": f"{key.title()} User",
            "role": role,
        },
        validator=has_json_object("id", "username", "email", "fullName", "role"),
        capture=capture_id(r, key),
    )
    return username


def create_project(r: ContractRunner, key: str, owner_id: int, name_suffix: str) -> None:
    r.check(
        f"Create project {key}",
        "POST",
        "/projects",
        200,
        json_body={
            "name": f"{r.prefix} {name_suffix}",
            "description": f"Contract project {name_suffix}",
            "ownerId": owner_id,
        },
        validator=has_json_object("id", "name", "description", "ownerId"),
        capture=capture_id(r, key),
    )


def create_ticket(
    r: ContractRunner,
    key: str,
    project_id: int,
    assignee_id: int | None,
    *,
    title: str,
    status: str = "TODO",
    priority: str = "MEDIUM",
    ticket_type: str = "BUG",
    due_date: str | None = None,
) -> None:
    body: dict[str, Any] = {
        "title": f"{r.prefix} {title}",
        "description": f"Contract test ticket for {title}",
        "status": status,
        "priority": priority,
        "type": ticket_type,
        "projectId": project_id,
    }
    if assignee_id is not None:
        body["assigneeId"] = assignee_id
    if due_date is not None:
        body["dueDate"] = due_date

    r.check(
        f"Create ticket {key}",
        "POST",
        "/tickets",
        200,
        json_body=body,
        validator=has_json_object(
            "id",
            "title",
            "description",
            "status",
            "priority",
            "type",
            "projectId",
            "assigneeId",
            "dueDate",
            "isOverdue",
        ),
        capture=capture_id(r, key),
    )


def run_contract(args: argparse.Namespace) -> int:
    prefix = args.prefix or "contract_" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    r = ContractRunner(args.base_url, prefix, args.verbose_body)

    if args.admin_token:
        r.tokens["admin"] = args.admin_token
    if args.developer_token:
        r.tokens["developer"] = args.developer_token

    r.section("Server Reachability")
    try:
        r.check(
            "Server responds on a known endpoint",
            "GET",
            "/users",
            {200, 401, 403},
            token=None,
            validator=empty_or_json,
        )
    except RuntimeError as exc:
        print(f"[FAIL] {exc}")
        print("Start the app first, then rerun this script.")
        return 2

    r.section("Users API - Allowed")
    setup_token = "admin" if "admin" in r.tokens else None
    admin_username = create_user(r, "admin", "ADMIN", token=setup_token)
    dev1_username = create_user(r, "dev1", "DEVELOPER", token=setup_token)
    dev2_username = create_user(r, "dev2", "DEVELOPER", token=setup_token)
    dev3_username = create_user(r, "dev3", "DEVELOPER", token=setup_token)

    admin_id = r.ids.get("admin")
    dev1_id = r.ids.get("dev1")
    dev2_id = r.ids.get("dev2")
    dev3_id = r.ids.get("dev3")
    if not all([admin_id, dev1_id, dev2_id, dev3_id]):
        r.skip("Dependent API checks", "User creation failed, so project/ticket setup cannot continue reliably.")
        return r.summary()

    r.section("Users API - Not Allowed")
    r.check(
        "Reject invalid email",
        "POST",
        "/users",
        400,
        token=None,
        json_body={"username": f"{prefix}_bad_email", "email": "not-email", "fullName": "Bad Email", "role": "DEVELOPER"},
        validator=error_json,
    )
    r.check(
        "Reject invalid role",
        "POST",
        "/users",
        400,
        token=None,
        json_body={
            "username": f"{prefix}_bad_role",
            "email": f"{prefix}_bad_role@example.com",
            "fullName": "Bad Role",
            "role": "QA",
        },
        validator=error_json,
    )
    r.section("Authentication API")
    r.check(
        "Login admin with default password",
        "POST",
        "/auth/login",
        200,
        token=None,
        json_body={"username": admin_username, "password": "secret"},
        validator=has_json_object("accessToken", "tokenType", "expiresIn"),
        capture=capture_token(r, "admin"),
    )
    r.check(
        "Login developer with default password",
        "POST",
        "/auth/login",
        200,
        token=None,
        json_body={"username": dev1_username, "password": "secret"},
        validator=has_json_object("accessToken", "tokenType", "expiresIn"),
        capture=capture_token(r, "developer"),
    )
    r.check("Get current user", "GET", "/auth/me", 200, token="admin", validator=field_equals("id", admin_id))
    r.check(
        "Reject invalid login",
        "POST",
        "/auth/login",
        401,
        token=None,
        json_body={"username": admin_username, "password": "wrong"},
        validator=error_json,
    )
    r.check(
        "Reject unauthenticated protected request",
        "GET",
        "/auth/me",
        401,
        token=None,
        validator=empty_or_json,
    )

    r.section("Users API - Authenticated")
    r.check("Get all users", "GET", "/users", 200, validator=has_json_array)
    r.check("Get user by id", "GET", f"/users/{dev1_id}", 200, validator=field_equals("id", dev1_id))
    r.check(
        "Update user fullName and role",
        "POST",
        f"/users/update/{dev3_id}",
        200,
        json_body={"fullName": "Updated Contract Developer", "role": "DEVELOPER"},
        validator=empty_or_json,
    )
    r.check("Reject missing user", "GET", "/users/999999999", 404, validator=error_json)

    r.section("Projects API - Allowed")
    create_project(r, "project", admin_id, "Primary")
    create_project(r, "other_project", admin_id, "Other")
    project_id = r.ids["project"]
    other_project_id = r.ids["other_project"]
    r.check("Get all projects", "GET", "/projects", 200, validator=has_json_array)
    r.check("Get project by id", "GET", f"/projects/{project_id}", 200, validator=field_equals("id", project_id))
    r.check(
        "Update project name and description",
        "PATCH",
        f"/projects/{project_id}",
        200,
        json_body={"name": f"{prefix} Primary Updated", "description": "Updated by contract runner"},
        validator=empty_or_json,
    )

    r.section("Projects API - Not Allowed")
    r.check(
        "Reject project with missing owner",
        "POST",
        "/projects",
        404,
        json_body={"name": f"{prefix} Bad Owner", "description": "Bad owner", "ownerId": 999999999},
        validator=error_json,
    )
    r.check(
        "Reject blank project update",
        "PATCH",
        f"/projects/{project_id}",
        400,
        json_body={"name": " "},
        validator=error_json,
    )

    r.section("Tickets API - Allowed")
    create_ticket(
        r,
        "ticket",
        project_id,
        dev1_id,
        title="explicit assignee",
        priority="HIGH",
        ticket_type="BUG",
        due_date=iso_future(7),
    )
    create_ticket(
        r,
        "blocker",
        project_id,
        dev2_id,
        title="blocker",
        priority="MEDIUM",
        ticket_type="TECHNICAL",
    )
    create_ticket(
        r,
        "auto_ticket",
        project_id,
        None,
        title="auto assigned",
        priority="LOW",
        ticket_type="FEATURE",
    )
    create_ticket(
        r,
        "other_ticket",
        other_project_id,
        dev2_id,
        title="other project",
        priority="LOW",
        ticket_type="BUG",
    )
    ticket_id = r.ids["ticket"]
    blocker_id = r.ids["blocker"]
    auto_ticket_id = r.ids["auto_ticket"]
    other_ticket_id = r.ids["other_ticket"]

    r.check("Get tickets by project", "GET", f"/tickets?projectId={project_id}", 200, validator=list_contains_id(ticket_id))
    r.check("Get ticket by id", "GET", f"/tickets/{ticket_id}", 200, validator=field_equals("id", ticket_id))
    r.check(
        "Update ticket forward from TODO to IN_PROGRESS",
        "PATCH",
        f"/tickets/{ticket_id}",
        200,
        json_body={"status": "IN_PROGRESS", "priority": "MEDIUM", "assigneeId": dev2_id},
        validator=empty_or_json,
    )
    r.check("Get project workload", "GET", f"/projects/{project_id}/workload", 200, validator=has_json_array)
    r.check(
        "Auto-assigned ticket has assignee",
        "GET",
        f"/tickets/{auto_ticket_id}",
        200,
        validator=lambda response: (
            isinstance(response.json, dict) and response.json.get("assigneeId") is not None,
            "expected assigneeId to be set by auto-assignment",
        ),
    )

    r.section("Tickets API - Not Allowed")
    r.check(
        "Reject missing projectId query parameter",
        "GET",
        "/tickets",
        400,
        validator=error_json,
    )
    r.check(
        "Reject invalid ticket status enum",
        "POST",
        "/tickets",
        400,
        json_body={
            "title": f"{prefix} bad status",
            "description": "Bad status",
            "status": "OPEN",
            "priority": "LOW",
            "type": "BUG",
            "projectId": project_id,
        },
        validator=error_json,
    )
    r.check(
        "Reject backward lifecycle transition",
        "PATCH",
        f"/tickets/{ticket_id}",
        400,
        json_body={"status": "TODO"},
        validator=error_json,
    )
    r.check(
        "Reject missing assignee",
        "PATCH",
        f"/tickets/{ticket_id}",
        404,
        json_body={"assigneeId": 999999999},
        validator=error_json,
    )
    r.skip(
        "Optimistic locking via HTTP",
        "The README requires concurrent-update protection, but the HTTP contract exposes no version or If-Match field to drive a deterministic black-box conflict.",
    )

    r.section("Dependencies API - Allowed")
    r.check(
        "Add dependency",
        "POST",
        f"/tickets/{ticket_id}/dependencies",
        200,
        json_body={"blockedBy": blocker_id},
        validator=empty_or_json,
    )
    r.check(
        "List dependencies",
        "GET",
        f"/tickets/{ticket_id}/dependencies",
        200,
        validator=list_contains_id(blocker_id),
    )
    r.check(
        "Remove dependency",
        "DELETE",
        f"/tickets/{ticket_id}/dependencies/{blocker_id}",
        200,
        validator=empty_or_json,
    )

    r.section("Dependencies API - Not Allowed")
    r.check(
        "Reject self dependency",
        "POST",
        f"/tickets/{ticket_id}/dependencies",
        400,
        json_body={"blockedBy": ticket_id},
        validator=error_json,
    )
    r.check(
        "Reject cross-project dependency",
        "POST",
        f"/tickets/{ticket_id}/dependencies",
        400,
        json_body={"blockedBy": other_ticket_id},
        validator=error_json,
    )
    r.check(
        "Add blocker before DONE transition test",
        "POST",
        f"/tickets/{ticket_id}/dependencies",
        200,
        json_body={"blockedBy": blocker_id},
        validator=empty_or_json,
    )
    r.check(
        "Move ticket to IN_REVIEW",
        "PATCH",
        f"/tickets/{ticket_id}",
        200,
        json_body={"status": "IN_REVIEW"},
        validator=empty_or_json,
    )
    r.check(
        "Reject DONE when unresolved blocker exists",
        "PATCH",
        f"/tickets/{ticket_id}",
        400,
        json_body={"status": "DONE"},
        validator=error_json,
    )
    r.check(
        "Remove blocker after DONE rejection",
        "DELETE",
        f"/tickets/{ticket_id}/dependencies/{blocker_id}",
        200,
        validator=empty_or_json,
    )
    r.check(
        "Allow DONE after blocker removed",
        "PATCH",
        f"/tickets/{ticket_id}",
        200,
        json_body={"status": "DONE"},
        validator=empty_or_json,
    )
    r.check(
        "Reject updating DONE ticket",
        "PATCH",
        f"/tickets/{ticket_id}",
        400,
        json_body={"title": f"{prefix} cannot update done"},
        validator=error_json,
    )

    r.section("Comments and Mentions API - Allowed")
    r.check(
        "Add comment with case-insensitive mention",
        "POST",
        f"/tickets/{blocker_id}/comments",
        200,
        json_body={"authorId": dev1_id, "content": f"Please check this @{dev2_username.upper()}"},
        validator=has_json_object("id", "ticketId", "authorId", "content", "mentionedUsers"),
        capture=capture_id(r, "comment"),
    )
    comment_id = r.ids["comment"]
    r.check("List comments for ticket", "GET", f"/tickets/{blocker_id}/comments", 200, validator=has_json_array)
    r.check(
        "Get mentions for user",
        "GET",
        f"/users/{dev2_id}/mentions?page=1&pageSize=10",
        200,
        validator=mentions_include(dev2_id),
    )
    r.check(
        "Update comment and recalculate mentions",
        "PATCH",
        f"/tickets/{blocker_id}/comments/{comment_id}",
        200,
        json_body={"content": f"Now mentioning @{dev3_username} instead"},
        validator=empty_or_json,
    )
    r.check(
        "Get recalculated mentions for new user",
        "GET",
        f"/users/{dev3_id}/mentions?page=1&pageSize=10",
        200,
        validator=mentions_include(dev3_id),
    )

    r.section("Comments and Mentions API - Not Allowed")
    r.check(
        "Reject comment with missing author",
        "POST",
        f"/tickets/{blocker_id}/comments",
        404,
        json_body={"authorId": 999999999, "content": "Unknown author"},
        validator=error_json,
    )
    r.check(
        "Reject blank comment update",
        "PATCH",
        f"/tickets/{blocker_id}/comments/{comment_id}",
        400,
        json_body={"content": " "},
        validator=error_json,
    )
    r.check(
        "Reject invalid mention page",
        "GET",
        f"/users/{dev2_id}/mentions?page=0&pageSize=10",
        400,
        validator=error_json,
    )

    r.section("Attachments API - Allowed")
    body, content_type = multipart({}, {"file": ("notes.txt", "text/plain", b"hello from contract runner\n")})
    r.check(
        "Upload allowed text attachment",
        "POST",
        f"/tickets/{blocker_id}/attachments",
        200,
        data=body,
        content_type=content_type,
        validator=has_json_object("id", "ticketId", "filename", "contentType"),
        capture=capture_id(r, "attachment"),
    )
    attachment_id = r.ids["attachment"]
    r.check(
        "Delete attachment",
        "DELETE",
        f"/tickets/{blocker_id}/attachments/{attachment_id}",
        200,
        validator=empty_or_json,
    )

    r.section("Attachments API - Not Allowed")
    body, content_type = multipart({}, {"file": ("payload.json", "application/json", b'{"bad": true}')})
    r.check(
        "Reject unsupported attachment content type",
        "POST",
        f"/tickets/{blocker_id}/attachments",
        400,
        data=body,
        content_type=content_type,
        validator=error_json,
    )
    body, content_type = multipart({}, {"file": ("too-large.txt", "text/plain", b"x" * (10 * 1024 * 1024 + 1))})
    r.check(
        "Reject attachment over 10 MB",
        "POST",
        f"/tickets/{blocker_id}/attachments",
        {0, 400, 413},
        data=body,
        content_type=content_type,
        validator=upload_rejected,
    )

    r.section("CSV Export and Import API")
    r.check("Export tickets as CSV", "GET", f"/tickets/export?projectId={project_id}", 200, validator=csv_export)
    csv_content = (
        "title,description,status,priority,type,assigneeId\n"
        f"\"{prefix} imported, with comma\",\"quoted \"\"description\"\"\",TODO,LOW,BUG,{dev1_id}\n"
        f"{prefix} bad row,invalid enum,OPEN,LOW,BUG,{dev1_id}\n"
    ).encode("utf-8")
    body, content_type = multipart({"projectId": str(project_id)}, {"file": ("tickets.csv", "text/csv", csv_content)})
    r.check(
        "Import CSV with one good row and one failed row",
        "POST",
        "/tickets/import",
        200,
        data=body,
        content_type=content_type,
        validator=import_summary,
    )
    body, content_type = multipart({"projectId": str(project_id)}, {"file": ("bad.csv", "text/csv", b"title,status\nOnly title,TODO\n")})
    r.check(
        "Reject CSV missing required headers",
        "POST",
        "/tickets/import",
        400,
        data=body,
        content_type=content_type,
        validator=error_json,
    )

    r.section("Soft Delete API - Allowed")
    r.check("Soft-delete ticket", "DELETE", f"/tickets/{blocker_id}", 200, validator=empty_or_json)
    r.check(
        "Deleted ticket is hidden from standard ticket list",
        "GET",
        f"/tickets?projectId={project_id}",
        200,
        validator=list_not_contains_id(blocker_id),
    )
    r.check(
        "List soft-deleted tickets as admin",
        "GET",
        f"/tickets/deleted?projectId={project_id}",
        200,
        token="admin",
        validator=list_contains_id(blocker_id),
    )
    r.check("Restore soft-deleted ticket as admin", "POST", f"/tickets/{blocker_id}/restore", 200, token="admin", validator=empty_or_json)
    r.check("Soft-delete project", "DELETE", f"/projects/{other_project_id}", 200, validator=empty_or_json)
    r.check("List soft-deleted projects as admin", "GET", "/projects/deleted", 200, token="admin", validator=list_contains_id(other_project_id))
    r.check("Restore soft-deleted project as admin", "POST", f"/projects/{other_project_id}/restore", 200, token="admin", validator=empty_or_json)

    r.section("Soft Delete API - Not Allowed")
    r.check(
        "Reject developer listing deleted projects",
        "GET",
        "/projects/deleted",
        {401, 403},
        token="developer",
        validator=empty_or_json,
    )
    r.check(
        "Reject developer listing deleted tickets",
        "GET",
        f"/tickets/deleted?projectId={project_id}",
        {401, 403},
        token="developer",
        validator=empty_or_json,
    )

    r.section("Audit Log API")
    r.check("List audit logs", "GET", "/audit-logs", 200, validator=has_json_array)
    r.check("Filter audit logs by action", "GET", "/audit-logs?action=CREATE", 200, validator=has_json_array)
    r.check("Reject invalid audit action enum", "GET", "/audit-logs?action=BOGUS", 400, validator=error_json)

    if args.include_scheduler:
        r.section("Auto-Escalation Scheduler")
        create_ticket(
            r,
            "overdue_low",
            project_id,
            dev1_id,
            title="overdue low priority",
            priority="LOW",
            ticket_type="BUG",
            due_date=iso_past(1),
        )
        create_ticket(
            r,
            "overdue_critical",
            project_id,
            dev1_id,
            title="overdue critical priority",
            priority="CRITICAL",
            ticket_type="BUG",
            due_date=iso_past(1),
        )
        low_id = r.ids["overdue_low"]
        critical_id = r.ids["overdue_critical"]
        print(f"       Waiting up to {args.scheduler_wait_seconds}s for the scheduler to run...")
        deadline = time.time() + args.scheduler_wait_seconds
        low_seen = False
        critical_seen = False
        while time.time() < deadline and not (low_seen and critical_seen):
            low_response = r.request("GET", f"/tickets/{low_id}")
            critical_response = r.request("GET", f"/tickets/{critical_id}")
            try:
                low_seen = isinstance(low_response.json, dict) and low_response.json.get("priority") in {"MEDIUM", "HIGH", "CRITICAL"}
                critical_seen = isinstance(critical_response.json, dict) and critical_response.json.get("isOverdue") is True
            except json.JSONDecodeError:
                pass
            if not (low_seen and critical_seen):
                time.sleep(5)
        r.check(
            "Overdue LOW ticket was escalated",
            "GET",
            f"/tickets/{low_id}",
            200,
            validator=lambda response: (
                isinstance(response.json, dict) and response.json.get("priority") in {"MEDIUM", "HIGH", "CRITICAL"},
                "expected priority to be promoted above LOW",
            ),
        )
        r.check(
            "Overdue CRITICAL ticket has isOverdue=true",
            "GET",
            f"/tickets/{critical_id}",
            200,
            validator=lambda response: (
                isinstance(response.json, dict) and response.json.get("isOverdue") is True,
                "expected isOverdue=true for overdue CRITICAL ticket",
            ),
        )
    else:
        r.skip("Auto-escalation scheduler", "Pass --include-scheduler to wait for and verify the background scheduler.")

    r.section("Authentication Logout")
    r.check("Logout developer token", "POST", "/auth/logout", 200, token="developer", validator=empty_or_json)
    r.check("Reject logged-out developer token", "GET", "/auth/me", 401, token="developer", validator=empty_or_json)

    return r.summary()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run IssueFlow HTTP contract checks.")
    parser.add_argument("--base-url", default=os.environ.get("ISSUEFLOW_BASE_URL", "http://localhost:8080"))
    parser.add_argument("--prefix", default=os.environ.get("ISSUEFLOW_TEST_PREFIX"))
    parser.add_argument("--admin-token", default=os.environ.get("ISSUEFLOW_ADMIN_TOKEN"))
    parser.add_argument("--developer-token", default=os.environ.get("ISSUEFLOW_DEVELOPER_TOKEN"))
    parser.add_argument("--include-scheduler", action="store_true", help="Wait for and verify auto-escalation.")
    parser.add_argument("--scheduler-wait-seconds", type=int, default=75)
    parser.add_argument("--verbose-body", action="store_true", help="Print full response bodies.")
    return parser.parse_args()


if __name__ == "__main__":
    sys.exit(run_contract(parse_args()))
