# Target stand requirements

What the shared test stand needs, so the team can choose where it runs. The stand hosts the deployed system, monitoring and the load, security and failure tests from the spec (sections 7 and 11).

## Why it matters

Load tests can't be trusted from a laptop: the load generator must not share a machine with the system under test. Without a stand, the first load test, the Telegram webhook and the final acceptance run can't be done.

## Hosts

The figures are starting estimates for the targets in NFR-PERF (1,000 concurrent users, 500 requests/s, p95 up to 300 ms). They have to be confirmed by the first load test.

| Host | Runs | CPU | RAM | Disk |
|---|---|---|---|---|
| Application | `api` (2 instances), `worker`, `bot`, reverse proxy, Prometheus, Grafana | 4 vCPU | 8 GB | 50 GB SSD |
| Database | PostgreSQL + PostGIS, Redis | 4 vCPU | 16 GB | 100 GB SSD |
| Load generator | Gatling only | 2-4 vCPU | 4-8 GB | 20 GB |

- The first two hosts may be merged into one with 8 vCPU and 24 GB for a smaller budget. Results then show less about scaling, and a database under load competes with the application for CPU.
- The load generator must always be a separate machine. It can be started only for test runs.
- Linux on x86-64: the PostGIS image is published for amd64 only.
- Disk must fit synthetic import files of up to 10^7 rows, database indexes and backups.

## Network

| Direction | Needed for | Check |
|---|---|---|
| Outbound to `api.telegram.org:443` | Telegram bot | `curl https://api.telegram.org` from the host |
| Outbound to the LLM provider | Chat bot (provider is not chosen yet) | Same, once the provider is known |
| Outbound to Docker Hub and `ghcr.io` | Pulling images | `docker pull`; a registry mirror may be needed |
| Inbound HTTPS with a valid certificate on port 443, 80, 88 or 8443 | Telegram webhook (FR-TG-09) and public access (NFR-SEC-02) | `setWebhook` succeeds |
| Between the load generator and the application host | Load tests | Bandwidth must not be the bottleneck |

Grafana, Prometheus and the database must not be reachable from the internet.

## Options

| Option | Advantages | Risks to check |
|---|---|---|
| VPS or cloud in Russia | Personal data stays in Russia (NFR-SEC-07, Federal Law 152-FZ) | Access to the Telegram API and the LLM provider, Docker Hub access, cost |
| VPS or cloud abroad | Telegram API and LLM providers are usually reachable | Storing personal data abroad, payment options |
| University server | Probably free | External access, a public HTTPS address, admin rights, availability |
| Team members' machines on one network | Free, enough for early load tests | No public HTTPS, not available around the clock, results are hard to reproduce |

## Decision needed

| Question | Owner |
|---|---|
| Which option, and who pays for it | Team lead and team |
| Who has administrator access | Team lead, QA/DevOps |
| Which LLM provider must be reachable | Team lead, Telegram/chat developer |

Deployment does not depend on the choice: services run from Docker images, so moving to another host only needs Docker and the environment variables described in [secrets.md](secrets.md).
