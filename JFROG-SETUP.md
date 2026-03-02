# JFrog Artifactory — Setup Guide (Maven)

This guide covers the one-time JFrog Artifactory configuration required to run the Seal Security + JFrog remediation workflow for this Maven project.

---

## Overview

The workflow routes all artifact traffic through JFrog Artifactory. Two remote repositories are required:

| Repo Key | Type | Proxies | Purpose |
|---|---|---|---|
| `seal-cli` | Generic | `https://cli.sealsecurity.io/authenticated/jfrog` | Routes Seal CLI API calls (project init, remote rules) through JFrog |
| `seal-mvn` | Maven | `https://maven.sealsecurity.io` | Routes sealed Maven package downloads through JFrog |

---

## Prerequisites

- A Seal Security account with an **artifact server token** (see [Generating a Token](https://docs.sealsecurity.io))
- Admin access to a JFrog Artifactory instance

---

## Step 1 — Create the `seal-cli` Remote Repository

This repository proxies Seal CLI API calls through JFrog. It requires several non-default settings to forward requests correctly.

1. Go to **Administration → Repositories → Remote** and click **New Remote Repository**.
2. Configure as follows:

   | Field | Value |
   |---|---|
   | **Package Type** | Generic |
   | **Repository Key** | `seal-cli` |
   | **URL** | `https://cli.sealsecurity.io/authenticated/jfrog` |
   | **Username** | `jfrog` |
   | **Password / Access Token** | Your Seal artifact server token (`SEAL_TOKEN`) |

3. Under **Advanced**, enable/disable these settings:

   | Setting | Value | Why |
   |---|---|---|
   | **Propagate Query Parameters** | On | Seal CLI passes query params in API requests; they must be forwarded |
   | **Disable URL Normalization** | On | Prevents JFrog from rewriting Seal's API endpoint URLs |
   | **Store Artifacts Locally** | Off | This repo proxies API calls, not downloadable artifacts |
   | **Pass Through** | On | Enables JFrog to forward POST requests (required for CLI initialization and rule queries) |

4. Click **Save & Finish**.

> **Why is `Pass Through` critical?** Without it, JFrog only proxies GET requests. The Seal CLI sends POST requests to initialize projects and query remote rules. With `Pass Through` off, JFrog returns an empty response and the CLI fails with `failed querying project from server`.

---

## Step 2 — Create the `seal-mvn` Remote Repository

This repository proxies sealed Maven package downloads through JFrog.

1. Go to **Administration → Repositories → Remote** and click **New Remote Repository**.
2. Configure as follows:

   | Field | Value |
   |---|---|
   | **Package Type** | Maven |
   | **Repository Key** | `seal-mvn` (the workflow references this key directly) |
   | **URL** | `https://maven.sealsecurity.io` |
   | **Username** | `jfrog` |
   | **Password / Access Token** | Your Seal artifact server token (`SEAL_TOKEN`) |

3. Keep all other settings at their defaults.
4. Click **Save & Finish**.

After a successful remediation run, you can verify in JFrog that sealed package versions (e.g. `snakeyaml-1.33+sp1.jar`) appear cached in the `seal-mvn` repository.

---

## Step 3 — Generate a JFrog Reference Token

The workflow authenticates with JFrog using a reference token (stored as `JFROG_TOKEN`).

1. Go to your JFrog user profile → **Edit Profile → Authentication Tokens**.
2. Click **Generate Token** and configure:

   | Field | Value |
   |---|---|
   | **Token Scope** | User |
   | **Username** | Your JFrog username |
   | **Expiration** | Never |
   | **Create Reference Token** | On |

3. Click **Generate** and copy the **reference token** (not the access token).

---

## Step 4 — Configure GitHub Secrets

Set the following secrets on the GitHub repository (`Settings → Secrets and variables → Actions`):

| Secret | Value |
|---|---|
| `SEAL_TOKEN` | Seal artifact server token (same token used as password in JFrog repos above) |
| `JFROG_TOKEN` | JFrog reference token from Step 3 |
| `JFROG_HOST` | Your JFrog hostname, e.g. `mycompany.jfrog.io` |
| `JFROG_USER` | Your JFrog username |
| `SNYK_TOKEN` | Snyk API token |
| `SNYK_ORG_ID` | Snyk organization ID |
| `SNYK_PROJECT_ID` | Snyk project ID for this repo |
| `NGROK_TOKEN` | ngrok authtoken |

Also set the following **repository variable** (not secret — it needs to be visible in logs):

| Variable | Value |
|---|---|
| `NGROK_URL` | Your reserved ngrok domain, e.g. `my-domain.ngrok.dev` |

---

## How the Workflow Uses These Repositories

```
Seal CLI (in CI)
  │
  ├─ API calls (project init, remote rules)
  │    └─▶ JFrog seal-cli repo ──▶ https://cli.sealsecurity.io/authenticated/jfrog
  │
  └─ Maven artifact downloads (sealed versions)
       └─▶ JFrog seal-mvn repo ──▶ https://maven.sealsecurity.io
```

The workflow sets these environment variables to enable JFrog mode:

```yaml
SEAL_JFROG_ENABLED: "1"
SEAL_JFROG_AUTH_TOKEN: ${{ secrets.JFROG_TOKEN }}
SEAL_JFROG_INSTANCE_HOST: ${{ secrets.JFROG_HOST }}
```

A `~/.m2/settings.xml` is also written at runtime to point Maven at the `seal-mvn` JFrog repo.
