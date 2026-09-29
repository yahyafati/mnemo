# ADR 0005: AI providers, API keys and network rules

- **Status:** Accepted
- **Date:** 2026-09-29
- **Context for:** ROADMAP Phase 3; PROJECT_OVERVIEW §5; ARCHITECTURE §5.3, §10

## Decision

### Providers are rows, one client speaks to all of them

- `ai_providers`, `ai_models`, `ai_task_routes` and `ai_usage` (schema v3, `Migration2To3`).
  Every provider is called through `OpenAiCompatibleClient` (`:core:ai`, OkHttp + `okhttp-sse`):
  `GET /models`, `POST /chat/completions`, plain or streamed. No vendor SDKs.
- Presets (`AiProviderPresets`, in `:core:model` so the settings UI can use them) only pre-fill
  the editor. A provider made from one is an ordinary provider.
- `AiProviderRepository.routeFor(task)` uses the task's own route if its provider is enabled,
  otherwise the **default provider: the first enabled provider with a model**, in the user's
  order. No usable provider → `null`, and the caller shows `AiSetupPrompt` (`:core:ui`).

### API keys never enter the database

The architecture first put `encryptedApiKey` on `AiProviderEntity`. It lives elsewhere instead:

- `SecretCipher` (`:core:security`): AES-256-GCM with a non-exportable Android Keystore key,
  created on first use. The provider id is the GCM associated data, so a ciphertext only
  decrypts for the provider it was made for.
- `SecretStore` keeps one encrypted file per provider in `noBackupFilesDir/secrets`, named by
  the SHA-256 of the id. A key is decrypted only to build a single request's `ProviderConfig`,
  whose `toString()` redacts it.

Why not a column: Mnemo's backups copy the database file itself (ADR 0004), and Android Auto
Backup copies the database directory. A column would put ciphertext into every backup; keeping
it out would need a scrub step in each. Outside the database, **no backup, export or Auto Backup
can contain a key by construction** (`noBackupFilesDir` is never backed up), and nothing needs to
remember to strip it. The ciphertext would be useless elsewhere anyway: the Keystore key doesn't
survive a restore to another device.

Consequences:

- Restoring a backup on the same device keeps working keys (same provider ids, same Keystore
  key). On a new device, providers come back without keys; the editor shows "No key" and a test
  reports `KeyUnavailable` if a stale file can't be decrypted.
- Keys of providers that no longer exist (after a restore, or an interrupted save) are pruned
  when the providers screen opens.
- Robolectric has no Keystore: tests bind `SoftwareSecretCipher` (`:core:testing`) instead of
  `CipherModule`.

### Network rules

- HTTPS is required, except for providers the user marked **local** whose host is a local
  address: loopback, private (10/8, 172.16/12, 192.168/16), link-local, CGNAT 100.64/10 (Tailscale),
  IPv6 ULA/link-local, single-label names and `.local`/`.lan`/`.home.arpa`/`.internal`. DNS is
  never consulted. `AiEndpoint.check` implements this; the editor uses it to explain problems,
  and the client checks it again before every request (`MnemoError.Blocked`).
- A network security config can only list host names, not address ranges, and local servers
  are normally reached by LAN IP. So `network_security_config.xml` can't be the enforcement
  point for "local only": the platform allows cleartext, and the rule is enforced in code by the
  app's only network client. The config pins HTTPS for the hosted presets' domains and trusts
  only system CAs, so a user-installed CA can't intercept keys.
- Redirects are never followed: a redirect could downgrade to HTTP or carry custom auth headers
  to another host. No HTTP cache, no cookies, no logging interceptor.

### Test connection and capabilities

`ConnectionProbe` lists the models, then with the chosen model (or the only one a server lists):

1. a streamed one-token completion. JSON instead of SSE, or a 400 about `stream`, means no
   streaming. A 400 asking for `max_completion_tokens` (newer OpenAI models) retries with it.
2. a one-token completion with a `json_schema` `response_format`. Accepted means structured
   output is supported. A server that silently ignores the field looks supported too; Phase 4
   parses tolerantly and repairs, so a false positive costs little.
3. vision comes from the model list where the provider describes it (OpenRouter), otherwise
   from the model's name. Not probed: it would need an image upload.

Capabilities are editable. A user edit sticks (`capabilitiesSetByUser`). Embedding, speech and
image models are left out of the picker but can be typed in.

### Privacy notice and usage

- Before the first request to a provider, `AiDisclosureDialog` says what is sent and to which
  host. It is recorded per provider (`disclosureAcceptedAt`); Phase 4 shows it before the first
  real request to a provider whose notice wasn't accepted.
- `ai_usage` logs each request's reported tokens per provider and task (connection tests have no
  task). It stays on the device and never leaves it.

### Room for on-device models (PROJECT_OVERVIEW §11)

On-device models (Gemini Nano / AICore) are left for later, but nothing here blocks them. Callers
ask `AiProviderRepository` for a route and will send requests through a data-layer repository
(Phase 4), never through `OpenAiCompatibleClient` directly. An on-device provider can be a row
with its own kind (no base URL, no key, no network notice) that the repository dispatches to a
different engine.

## Alternatives considered

- **EncryptedSharedPreferences** (Jetpack Security): deprecated, and a prefs file under
  `shared_prefs/` would need its own backup exclusions.
- **Encrypted column + scrub on backup**: works, but every new backup/export path must remember
  the scrub, and a missed one leaks ciphertext.
- **`base-config cleartextTrafficPermitted="false"` with local hosts listed**: can't cover LAN IPs,
  which is how Ollama and LM Studio are reached from a phone.
