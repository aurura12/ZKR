#!/usr/bin/env python3
import argparse
import mimetypes
import os
import smtplib
import ssl
from dataclasses import dataclass
from email.message import EmailMessage
from email.utils import formataddr
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ENV_FILE = REPO_ROOT / ".env"
DEFAULT_RECIPIENT = "2310406891@qq.com"
DEFAULT_SENDER_NAME = "我是5090agent"
TRUE_VALUES = {"1", "true", "yes", "on"}
PURPOSE_SUBJECT_PREFIX = {
    "authorization": "[授权]",
    "question": "[问询]",
    "notification": "[通知]",
}


@dataclass
class MailSettings:
    host: str
    port: int
    username: str
    password: str
    auth: bool
    starttls: bool
    default_to: str
    sender_name: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Send an email using the repo-local email skill."
    )
    parser.add_argument("--to", help="Recipient email address")
    parser.add_argument("--subject", required=True, help="Email subject")
    parser.add_argument(
        "--env-file",
        default=str(DEFAULT_ENV_FILE),
        help="Path to the .env file that stores SMTP settings",
    )
    parser.add_argument("--dry-run", action="store_true", help="Validate and print without sending")
    parser.add_argument(
        "--attach",
        action="append",
        default=[],
        help="Attach a file to the email; repeat for multiple files",
    )
    parser.add_argument(
        "--sender-name",
        help="Visible sender name for the From header and default signature",
    )
    parser.add_argument(
        "--signature",
        help="Signature text appended to the body; defaults to sender name",
    )
    parser.add_argument("--no-signature", action="store_true", help="Do not append a signature")
    parser.add_argument(
        "--purpose",
        choices=sorted(PURPOSE_SUBJECT_PREFIX.keys()),
        help="Optional purpose metadata for subject prefixing",
    )
    parser.add_argument(
        "--prefix-purpose",
        action="store_true",
        help="Prefix the subject with the selected purpose label",
    )

    body_group = parser.add_mutually_exclusive_group(required=True)
    body_group.add_argument("--message", help="Plain text email body")
    body_group.add_argument("--message-file", help="Read plain text email body from file")
    return parser.parse_args()


def load_env_file(env_path: Path) -> dict[str, str]:
    if not env_path.exists():
        return {}

    values: dict[str, str] = {}
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if value.startswith(("\"", "'")) and value.endswith(("\"", "'")) and len(value) >= 2:
            value = value[1:-1]
        values[key] = value
    return values


def env_or_file(key: str, file_values: dict[str, str], default: str = "") -> str:
    value = os.getenv(key)
    if value is not None and value != "":
        return value
    return file_values.get(key, default)


def first_value(file_values: dict[str, str], *keys: str, default: str = "") -> str:
    for key in keys:
        value = os.getenv(key)
        if value is not None and value != "":
            return value
        value = file_values.get(key)
        if value is not None and value != "":
            return value
    return default


def as_bool(value: str, default: bool) -> bool:
    if value is None or value == "":
        return default
    return value.strip().lower() in TRUE_VALUES


def read_settings(env_file: Path) -> MailSettings:
    file_values = load_env_file(env_file)

    port_value = env_or_file("SPRING_MAIL_PORT", file_values, "587")
    try:
        port = int(port_value)
    except ValueError as exc:
        raise SystemExit(f"Invalid SPRING_MAIL_PORT: {port_value}") from exc

    settings = MailSettings(
        host=env_or_file("SPRING_MAIL_HOST", file_values, "smtp.qq.com"),
        port=port,
        username=env_or_file("SPRING_MAIL_USERNAME", file_values),
        password=env_or_file("SPRING_MAIL_PASSWORD", file_values),
        auth=as_bool(env_or_file("SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH", file_values, "true"), True),
        starttls=as_bool(
            env_or_file("SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE", file_values, "true"),
            True,
        ),
        default_to=first_value(
            file_values,
            "EMAIL_SKILL_DEFAULT_TO",
            "ZKR_ASSISTANT_MAIL_TO",
            default=DEFAULT_RECIPIENT,
        ),
        sender_name=first_value(
            file_values,
            "EMAIL_SKILL_SENDER_NAME",
            default=DEFAULT_SENDER_NAME,
        ),
    )

    missing = []
    if not settings.host:
        missing.append("SPRING_MAIL_HOST")
    if not settings.username:
        missing.append("SPRING_MAIL_USERNAME")
    if settings.auth and not settings.password:
        missing.append("SPRING_MAIL_PASSWORD")
    if missing:
        joined = ", ".join(missing)
        raise SystemExit(
            f"Missing mail settings: {joined}. QQ SMTP must use the authorization code, not the QQ login password."
        )

    return settings


def read_body(args: argparse.Namespace) -> str:
    if args.message is not None:
        body = args.message
    else:
        body_path = Path(args.message_file).expanduser().resolve()
        if not body_path.exists():
            raise SystemExit(f"Message file not found: {body_path}")
        body = body_path.read_text(encoding="utf-8")

    if not body.strip():
        raise SystemExit("Message body cannot be empty")
    return body.rstrip()


def build_subject(subject: str, purpose: str | None, prefix_purpose: bool) -> str:
    if not prefix_purpose or not purpose:
        return subject

    prefix = PURPOSE_SUBJECT_PREFIX.get(purpose)
    if not prefix or subject.startswith(prefix):
        return subject
    return f"{prefix} {subject}"


def apply_signature(body: str, signature: str | None, enabled: bool) -> str:
    if not enabled:
        return body
    if signature is None:
        signature = DEFAULT_SENDER_NAME

    normalized_signature = signature.strip()
    if not normalized_signature:
        return body
    if body.endswith(normalized_signature):
        return body
    return f"{body}\n\n{normalized_signature}"


def resolve_attachments(raw_paths: list[str]) -> list[Path]:
    attachments: list[Path] = []
    for raw_path in raw_paths:
        attachment_path = Path(raw_path).expanduser().resolve()
        if not attachment_path.exists():
            raise SystemExit(f"Attachment file not found: {attachment_path}")
        if not attachment_path.is_file():
            raise SystemExit(f"Attachment path is not a file: {attachment_path}")
        attachments.append(attachment_path)
    return attachments


def add_attachments(message: EmailMessage, attachments: list[Path]) -> None:
    for attachment_path in attachments:
        mime_type, _ = mimetypes.guess_type(attachment_path.name)
        if mime_type:
            maintype, subtype = mime_type.split("/", 1)
        else:
            maintype, subtype = "application", "octet-stream"

        with attachment_path.open("rb") as attachment_file:
            message.add_attachment(
                attachment_file.read(),
                maintype=maintype,
                subtype=subtype,
                filename=attachment_path.name,
            )


def build_message(
    settings: MailSettings,
    recipient: str,
    subject: str,
    body: str,
    sender_name: str,
    attachments: list[Path],
) -> EmailMessage:
    message = EmailMessage()
    message["From"] = formataddr((sender_name, settings.username))
    message["To"] = recipient
    message["Subject"] = subject
    message.set_content(body)
    add_attachments(message, attachments)
    return message


def send_message(settings: MailSettings, message: EmailMessage) -> None:
    try:
        with smtplib.SMTP(settings.host, settings.port, timeout=30) as smtp:
            smtp.ehlo()
            if settings.starttls:
                smtp.starttls(context=ssl.create_default_context())
                smtp.ehlo()
            if settings.auth:
                smtp.login(settings.username, settings.password)
            smtp.send_message(message)
    except (OSError, smtplib.SMTPException) as exc:
        raise SystemExit(
            f"Email send failed: {exc}. Check QQ SMTP host, mailbox, and authorization code configuration."
        ) from exc


def main() -> None:
    args = parse_args()
    settings = read_settings(Path(args.env_file).expanduser().resolve())

    recipient = (args.to or settings.default_to).strip()
    if not recipient:
        raise SystemExit("Recipient email cannot be empty")

    raw_subject = args.subject.strip()
    if not raw_subject:
        raise SystemExit("Subject cannot be empty")

    sender_name = (args.sender_name or settings.sender_name).strip()
    if not sender_name:
        raise SystemExit("Sender name cannot be empty")

    subject = build_subject(raw_subject, args.purpose, args.prefix_purpose)
    body = read_body(args)
    signature = None if args.no_signature else (args.signature or sender_name)
    body_with_signature = apply_signature(body, signature, not args.no_signature)
    attachments = resolve_attachments(args.attach)
    message = build_message(settings, recipient, subject, body_with_signature, sender_name, attachments)

    if args.dry_run:
        print("dry_run=true")
        print(f"to={recipient}")
        print(f"subject={subject}")
        print(f"from_display_name={sender_name}")
        print(f"smtp_host={settings.host}")
        print(f"smtp_port={settings.port}")
        print(f"smtp_auth={str(settings.auth).lower()}")
        print(f"smtp_starttls={str(settings.starttls).lower()}")
        print(f"body_chars={len(body_with_signature)}")
        print(f"attachments={len(attachments)}")
        for attachment in attachments:
            print(f"attachment={attachment}")
        return

    send_message(settings, message)
    print(f"Email sent to {recipient}")


if __name__ == "__main__":
    main()
