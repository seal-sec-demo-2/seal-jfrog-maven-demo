# How Seal Security Works — Concepts & Integration Guide

This document explains how Seal's fix modes work, how JFrog Artifactory fits in, and how Snyk integration works. These concepts apply to both the Maven and npm JFrog demo repos.

---

## The Core Idea: Transparent Fixes, No Code Changes

Seal Security patches vulnerable open-source dependencies **without requiring developers to change `pom.xml` or `package.json`**. The dependency file still references the original version — Seal intercepts the download at build time and substitutes a patched version instead.

```
pom.xml declares:    snakeyaml:1.33         (unchanged)
Maven actually gets: snakeyaml:1.33+sp1     ← Seal swaps it transparently
```

The patched version (`+sp1`) is API-compatible with the original — same method signatures, same behavior, just with the vulnerability removed. No breaking changes, trivial to roll back.

---

## Fix Modes

There are two ways to tell Seal *which* packages to fix and *which* patched versions to use.

### Remote Mode (recommended for production)

The Seal CLI queries the **Seal platform** at fix time to get the list of approved sealing rules. Security teams configure these rules centrally in the Seal UI (`app.sealsecurity.io/protection/rules`) — for example, "seal all vulnerable packages using the safest available version."

```
CI build starts
    └─► Seal CLI runs
            └─► Calls Seal platform: "what should I fix for this project?"
                    └─► Platform returns approved rules
                            └─► CLI swaps in sealed versions during build
```

**Who controls it:** Security team, via the Seal UI. Developers don't need to do anything.

**When to use it:** Default for most teams. Central control, audit trail, no files to commit.

### Local Mode (version-controlled fixes)

Instead of querying the Seal platform at build time, the CLI reads a `.seal-actions.yml` file committed to the repository. This file lists exactly which version overrides to apply.

```
CI build starts
    └─► Seal CLI runs
            └─► Reads .seal-actions.yml from repo root
                    └─► Applies the overrides defined in the file
                            └─► Swaps in sealed versions during build
```

**Example `.seal-actions.yml`:**
```yaml
overrides:
  org.yaml:snakeyaml:
    "1.33":
      use: 1.33+sp1
  com.fasterxml.jackson.core:jackson-databind:
    "2.13.1":
      use: 2.13.1+sp1
```

**Who controls it:** The file lives in the repo and is reviewed via pull request, just like any other code change. Seal can auto-generate this file and open a PR for review.

**When to use it:** Teams that want fixes version-controlled and peer-reviewed alongside their code. Also useful for air-gapped environments where CI cannot reach the Seal platform.

### All Mode

The CLI applies every sealed version Seal has available for vulnerable dependencies, without consulting the platform or a local config file. Useful for broad vulnerability sweeps or demos where you want to see the maximum possible remediation.

---

## Two Approaches, Same Result

In both modes, `pom.xml` and `package.json` are **never modified**. The fix is applied only during the build — the package manager downloads the sealed version as if it were the original.

| | Remote | Local |
|---|---|---|
| Fix rules source | Seal platform (UI) | `.seal-actions.yml` in repo |
| Developer action required | None | Review & merge Seal's PR |
| Platform connectivity needed | Yes | No (after file is committed) |
| Central security team control | ✅ Full | ⚠️ Partial (via PR review) |
| Works air-gapped | ❌ | ✅ |

### When would a team actually choose local over remote?

Functionally, the end result is identical — `snakeyaml:1.33` gets replaced by `snakeyaml:1.33+sp1` at build time either way. The choice comes down to operational preference:

- **Auditability** — some security or compliance teams need the fix to be a visible, reviewable artifact in the repo. With local mode the `.seal-actions.yml` goes through a PR, gets code-reviewed, and leaves a full git history trail. Remote mode is invisible to the repo.
- **Air-gapped CI** — if the build environment cannot reach the Seal platform at runtime, local mode works because the override file is already in the repo.
- **Predictability** — with remote mode, a security team updating central rules means the next build gets different fixes without any repo change. Local mode only changes when someone explicitly merges a PR.

**For most teams, remote mode is the right default.** Local mode is a more advanced operational consideration — relevant once a team has standardized on Seal and wants tighter change-control over which fixes are applied.

### What about JFrog — does the fix mode affect that?

No. JFrog is completely orthogonal to fix mode. Whether you use remote, local, or all, the sealed packages are still downloaded through JFrog the same way. The fix mode controls *which* fixes get applied; JFrog controls *where* the patched packages come from.

---

## Where JFrog Artifactory Fits In

JFrog Artifactory is about **where sealed packages are downloaded from** — it's independent of which fix mode you use.

### Without JFrog (direct)

```
Seal CLI ──────────────────────────► maven.sealsecurity.io
                                     npm.sealsecurity.io
```

The CLI downloads sealed packages directly from Seal's artifact servers. Works when CI runners have direct internet access.

### With JFrog (proxied)

```
Seal CLI ──► JFrog Artifactory ────► maven.sealsecurity.io
             (seal-mvn repo)         npm.sealsecurity.io
             (seal-npm repo)
```

The CLI downloads sealed packages through JFrog, which proxies the request to Seal's servers. JFrog caches the sealed packages locally after the first download.

**Why use JFrog?**
- CI runners are behind a firewall and can only reach JFrog
- Your organization already routes all artifact traffic through JFrog for compliance/audit reasons
- You want sealed packages cached in your own artifact server

**JFrog repos configured for this demo:**

| Repo Key | Type | Proxies |
|---|---|---|
| `seal-cli` | Generic | `https://cli.sealsecurity.io/authenticated/jfrog` |
| `seal-mvn` | Maven | `https://maven.sealsecurity.io` |
| `seal-npm` | npm | `https://npm.sealsecurity.io` |

The Seal CLI is pointed at JFrog via three environment variables:
```
SEAL_JFROG_ENABLED=1
SEAL_JFROG_AUTH_TOKEN=<jfrog-token>
SEAL_JFROG_INSTANCE_HOST=<jfrog-hostname>
```

**After a workflow run**, you can verify the integration worked by checking JFrog Artifactory — sealed package artifacts (e.g. `snakeyaml-1.33+sp1.jar`, `ejs-2.7.4-sp2.tgz`) will appear cached in the `seal-mvn` / `seal-npm` remote repositories.

---

## Where Snyk Fits In

Snyk integration is about **reporting** — telling Snyk that a vulnerability has been fixed so it stops showing as an open alert.

After Seal applies fixes during the build, the Seal CLI automatically calls the **Snyk API** to mark the remediated CVEs as resolved:

```
Seal CLI applies fixes
    └─► Calls Snyk API
            └─► Marks patched CVEs as "not vulnerable"
                    └─► Adds comment: "vulnerability patched by seal-security"
                            └─► Snyk dashboard reflects updated posture
```

**This is not a webhook.** It's a direct API call from the Seal CLI. No Snyk-side configuration is required beyond providing the token and project ID.

Vulnerabilities that Seal did *not* patch (e.g. if a sealed version isn't available yet) continue to appear in Snyk as open alerts. New vulnerabilities discovered after the build also continue to appear normally.

---

## Full Picture for These Demo Repos

```
GitHub Actions CI
│
├─ npm install / mvn dependency:resolve
│   └─ Package manager configured to use JFrog as registry
│       └─ JFrog proxies to Seal artifact server
│
├─ Seal CLI (seal-community/cli-action)
│   ├─ Fix mode: remote  → queries Seal platform for approved rules
│   │             local   → reads .seal-actions.yml from repo
│   │             all     → applies everything available
│   │
│   ├─ Downloads sealed packages via JFrog (SEAL_JFROG_ENABLED=1)
│   │
│   └─ Reports to Snyk API → marks patched CVEs as resolved
│
├─ mvn package / npm run start
│   └─ Builds/runs with sealed (patched) dependencies
│
└─ ngrok → exposes app publicly for live exploit demonstration
```
