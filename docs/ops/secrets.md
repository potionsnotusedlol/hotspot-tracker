# Secrets policy

Secrets never go into git, logs, issues, PR comments or AI assistants (NFR-SEC-02). Use placeholders such as `<TOKEN>` in examples.

## Where secrets live

| Environment | Storage |
|---|---|
| Local development | `.env` files, ignored by git. `ops/compose/.env.example` lists the variables with placeholder values |
| GitHub Actions | Repository secrets or Environments, read through `${{ secrets.NAME }}` |
| Stand | Environment variables on the host, readable only by the deploy user |

## Known secrets

| Secret | Used by | Local | CI | Stand |
|---|---|---|---|---|
| PostgreSQL passwords (superuser, migration role, application role) | Database, server | yes | no: Testcontainers creates its own | yes |
| JWT signing keys | Server | yes | no | yes |
| `BOT_TOKEN` | Telegram bot | test bot only | no | yes |
| LLM provider API key | Chat bot | own key if needed | no: tests use a fake model | yes |
| OAuth client secret | Server | if needed | no | yes |
| `NVD_API_KEY` | Dependency-Check | no | yes | no |
| `SONAR_TOKEN` | SonarCloud | no | yes | no |
| Android signing key and passwords | Release build | no | yes | no |

Add a new secret to this table and to the matching `.env.example`, with a placeholder value.

## Rules

- Each person uses their own local values and test bot. Don't share production values in chats.
- Grant CI workflows only the secrets they need. Pull requests from forks don't receive repository secrets.
- Keep GitHub secret scanning and push protection enabled.

## If a secret leaks

1. **Revoke or rotate it first.** For a Telegram token: `/revoke` in @BotFather.
2. Replace the value everywhere it is used.
3. Remove it from the branch, and tell the repository owner and the team.

Deleting the file is not enough: the value stays in git history, and a public repository can be copied within minutes. Only rotation makes a leaked secret useless.
