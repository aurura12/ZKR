# email skill

This is the repo-local email skill for this project.

Because platform-level skill registration is not available in this repository, `skills/email/` is the local source of truth for email sending behavior.

Defaults:

- recipient: `2310406891@qq.com`
- visible sender name: `我是5090agent`
- SMTP settings: reuse existing `SPRING_MAIL_*` values from `.env`

Use it with:

```bash
python3 skills/email/send.py --subject "主题" --message "正文"
```

Common options:

- `--to <email>` override recipient
- `--message-file <path>` load body from a file
- `--attach <path>` attach a file; repeat the flag for multiple files
- `--dry-run` validate settings without sending
- `--no-signature` keep the body unchanged

By default, the skill appends the signature `我是5090agent` to the body and uses the same text as the display name in the `From` header.
