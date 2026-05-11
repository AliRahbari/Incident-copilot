# Publishing Incident Copilot

This document is for **maintainers**. It explains how to release `incident-copilot-core` and `incident-copilot-spring-boot-starter` as binary artifacts. The demo app (`incident-copilot-app`) is **never** published — its `maven-deploy-plugin` is configured with `skip=true`.

There are three paths, ordered from least to most setup:

1. [Local install](#1-local-install) — for development and pre-publication consumers.
2. [GitHub Packages](#2-github-packages) — easy private/public distribution; consumers must configure a GitHub Maven repo + auth.
3. [Maven Central (Central Portal)](#3-maven-central-central-portal) — public, no consumer-side setup, but the most metadata and signing requirements.

---

## 0. One-time prerequisites (per path)

| Item | Local | GitHub Packages | Maven Central |
|---|:-:|:-:|:-:|
| JDK 21 + Maven 3.9 | ✅ | ✅ | ✅ |
| GitHub Personal Access Token with `write:packages` | — | ✅ | — |
| Verified Central Portal namespace (e.g. `io.github.<handle>`) | — | — | ✅ |
| GPG key, uploaded to a public keyserver | — | — | ✅ |
| Real `<licenses>`, `<developers>`, `<scm>` in parent POM | — | — | ✅ |

The parent POM ships with **`TODO` placeholders** for the metadata items above. The Maven Central path will fail validation until those are filled in.

---

## 1. Local install

The default during development.

```bash
mvn clean install
# Optional: skip tests for a faster install loop
mvn clean install -DskipTests
```

This installs:

- `com.incident:incident-copilot-core:0.1.0`
- `com.incident:incident-copilot-spring-boot-starter:0.1.0`

into `~/.m2/repository`. The demo app jar is also built there but it's not a library you consume.

Consumers on the same machine just declare the dependency in their own `pom.xml` — no `<repository>` block needed.

---

## 2. GitHub Packages

GitHub Packages is the recommended path for early public distribution while Central Portal namespace + license are still being settled.

### 2.1 Authenticate

Add a server entry to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <!-- Personal Access Token with the write:packages scope -->
      <password>${env.GITHUB_TOKEN}</password>
    </server>
  </servers>
</settings>
```

### 2.2 Deploy

```bash
mvn -Prelease,github -DskipTests deploy
```

The `github` profile points `distributionManagement` at:

```
https://maven.pkg.github.com/AliRahbari/Incident-copilot
```

The `release` profile attaches `-sources.jar` and `-javadoc.jar` (GPG signing is **not** required for GitHub Packages and is gated behind the `central` profile).

### 2.3 Consume from GitHub Packages

A downstream project must (a) add the repository, and (b) authenticate. In their `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github-incident-copilot</id>
    <url>https://maven.pkg.github.com/AliRahbari/Incident-copilot</url>
  </repository>
</repositories>
```

And in their `~/.m2/settings.xml` they need a matching `<server id="github-incident-copilot">` block with a GitHub username + token that has `read:packages`. This is the main friction point of GitHub Packages vs Maven Central.

---

## 3. Maven Central (Central Portal)

This uses the **modern Central Portal flow** via the [`central-publishing-maven-plugin`](https://central.sonatype.org/publish/publish-portal-maven/). Old legacy OSSRH staging is **not** wired into this build.

### 3.1 Fill in the metadata TODOs

The parent `pom.xml` contains `TODO` markers in these places — they must be replaced with real values before a Central release will pass validation:

- `<groupId>` — must match a namespace you have verified on the Central Portal (typically `io.github.<your-handle>`). The current `com.incident` will be rejected.
- `<url>` — public project page (the GitHub repo is fine).
- `<licenses>` — pick a license (Apache-2.0, MIT, …), add a LICENSE file, and reflect it here.
- `<developers>` — at least one developer entry with `id`, `name`, `email`.
- `<scm>` — connection / developer connection / URL.

Once those are filled in, **do not invent placeholder values** — Central enforces them.

### 3.2 GPG key

1. Generate a key: `gpg --full-generate-key` (RSA 4096 is fine).
2. Publish it: `gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>`.
3. Export the secret key for CI: `gpg --export-secret-keys --armor <KEYID> > release.key`.

Locally, `maven-gpg-plugin` will prompt for the passphrase. In CI, supply `MAVEN_GPG_PASSPHRASE` and import the key first (see `.github/workflows/publish.yml`).

### 3.3 Central Portal credentials

Generate a user token on https://central.sonatype.com under *Account → Generate User Token*, then add to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
      <password>YOUR_CENTRAL_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

### 3.4 Release

```bash
# Switch to a release version (no -SNAPSHOT)
mvn versions:set -DnewVersion=0.1.0
mvn -Prelease,central -DskipTests deploy

# Then bump to the next snapshot
mvn versions:set -DnewVersion=0.2.0-SNAPSHOT
git commit -am "Prepare next development iteration"
```

What happens during deploy with `-Prelease,central`:

1. Source jar and javadoc jar are attached.
2. All artifacts (POM, jar, sources, javadoc) are GPG-signed.
3. `central-publishing-maven-plugin` bundles and uploads the artifacts to the Central Portal staging deployment.
4. By default the plugin uses `autoPublish=true`, so a successful upload promotes to Central automatically. Set `<autoPublish>false</autoPublish>` in the plugin config if you prefer to inspect the staged deployment in the Portal UI first.

### 3.5 Consume from Maven Central

Nothing special — consumers just declare the dependency. No `<repository>` block needed.

---

## 4. Continuous publishing (GitHub Actions)

`.github/workflows/publish.yml` ships a minimal pipeline:

- on every push / PR: `mvn -B verify` (build + tests).
- on `workflow_dispatch` or a `release` event: `mvn -Prelease,github deploy` to GitHub Packages.

Central deployment is intentionally **not wired by default** — it requires the per-key/per-credential setup in §3 and a real groupId. Once those are in place, switch the `deploy` job to `-Prelease,central` and inject:

| Secret | Used for |
|---|---|
| `MAVEN_GPG_PRIVATE_KEY` | Imported with `gpg --import` before deploy |
| `MAVEN_GPG_PASSPHRASE` | Passed to `maven-gpg-plugin` |
| `CENTRAL_TOKEN_USERNAME` | `<server id="central">` username |
| `CENTRAL_TOKEN_PASSWORD` | `<server id="central">` password |

A `release` event is currently the only automatic Central trigger, so day-to-day pushes never accidentally publish.

---

## 5. What gets published, what doesn't

| Module | Local install | GitHub Packages | Maven Central |
|---|:-:|:-:|:-:|
| `incident-copilot-core` | ✅ | ✅ | ✅ |
| `incident-copilot-spring-boot-starter` | ✅ | ✅ | ✅ |
| `incident-copilot-app` | installed (because `install` is local) | **skipped** | **skipped** |

The app's deploy is disabled via `maven-deploy-plugin` `<skip>true</skip>`. It's a runnable demo, not a library — consumers should not pull it in.

---

## 6. Pre-release checklist

- [ ] `mvn clean verify` is green on JDK 21.
- [ ] Version in `pom.xml` is a non-SNAPSHOT release (`0.1.0`, not `0.1.0-SNAPSHOT`).
- [ ] `CHANGELOG.md` (when introduced) is updated.
- [ ] `README.md` install snippet shows the version being released.
- [ ] For Central: all `TODO` placeholders in the parent POM are replaced.
- [ ] For Central: GPG key passphrase is reachable on the release machine.
- [ ] Tag the release: `git tag v0.1.0 && git push origin v0.1.0`.
