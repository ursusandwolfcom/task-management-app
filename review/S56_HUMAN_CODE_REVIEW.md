# S56 human code review packet
Publication note: start with [the original-assignment guide](MATE_ASSIGNMENT_REVIEW.md) for a whole-project review. This packet covers PR #99 only. Diagrams,
findings distinguish the initial source snapshot 7cad968 from hotfix fa2c036. The hotfix is
implemented with new regressions; full exact-head/proposed-merge verification is required. These revisions
preserve the original finding and its fix separately. The initial packet remains available at commit 8e9e8e4.

Current code review target: PR #99, hotfix fa2c036c6140ec9945902ee759d671d1d60b6acc. Initial finding snapshot: 7cad968.
Base: dev 295fd9bdc294e499fb2a73cdfc890c4507c5a76a.
Scope: deletion reliability, original user-route aliases and personal Dropbox credential
protection. This is a view of the S56 changes and their wiring, not the complete application.
Existing context classes are included when they affect changed behavior.

R-01 is fixed in fa2c036 after a failing regression run. Fresh full checks and external approval
are required before integration. The original green suite missed this defect; new tests cover it.

## 1. Attachment classes and actual runtime wiring

Legend: `-->` dependency/delegation; `..|>` implements. Method arguments are abbreviated.
These are associations, not ownership/lifetime assertions. `[changed]` means in this PR's
diff; `[context]` means an existing class outside the diff.

```text
+-----------------------------------------------+
| AttachmentService [context, @Transactional]    |
| + upload(actorId, taskId, ...)                 |
| + download(actorId, attachmentId)              |
| + delete(actorId, attachmentId)                |
+-----------------------+-----------------------+
                        |
                        | stores
                        v
+-----------------------------------------------------------+
| <<interface>> AttachmentObjectStoreFactory [context]        |
| + forUser(userId): AttachmentObjectStore                    |
| + forAttachment(userId, externalId): AttachmentObjectStore  |
+-----------------------------------------------------------+
               ^                                  ^
               : implements                       : implements
+---------------------------------+   +---------------------------------------+
| MegaAwareObjectStoreFactory     |   | DefaultAttachmentObjectStoreFactory   |
| [context, injected outer bean]  |   | [changed]                             |
| - legacy: ...StoreFactory       |-->| - fallback: AttachmentObjectStore     |
| + forUser(...)                  |   | - accounts: DropboxAccountOperations  |
| + forAttachment(...)            |   | + forUser(...)                        |
+---------------------------------+   | + forAttachment(...)                  |
   Fixed: non-MEGA branch calls       +---------------------------------------+
   legacy.forAttachment(userId, externalId)  | creates/selects
   preserving reference-aware selection.    v
                           +------------------------------------------+
                           | <<interface>> AttachmentObjectStore      |
                           | + put(key, body, size): String           |
                           | + open(key, externalId): InputStream     |
                           | + delete(key, externalId): void          |
                           +------------------------------------------+
                             ^               ^                 ^
                             :               :                 : implements
                    +----------------+ +----------------+ +--------------------+
                    | LocalAttachment| | DualAttachment | | DropboxAttachment  |
                    | ObjectStore    | | ObjectStore    | | ObjectStore        |
                    | [context]      | | [changed]      | | [context]          |
                    +----------------+ +----------------+ +---------+----------+
                                        | local/dropbox             | client
                                        | both implement            v
                                        | AttachmentObjectStore  <<DropboxFileClient>>
                                        |                           ^ implements
                                        +--> the two stores         :
                                                         +----------------------+
                                                         | SdkDropboxFileClient |
                                                         | [changed]            |
                                                         | - client: DbxClientV2|
                                                         | + delete(path, id)   |
                                                         +----------+-----------+
                                                                    | Dropbox SDK
```

Long class names wrapped across lines retain their actual identifiers. The MEGA-specific
store branch is omitted beyond the outer factory because it is not changed in S56.
AttachmentStorageConfiguration constructs the default factory and wraps it with
MegaAwareObjectStoreFactory; AttachmentService receives this wrapper, not the default
factory directly. AttachmentService also depends on AttachmentAccess, AttachmentRepository,
IdentityOperations, ActivityOperations and OutboxOperations; these are omitted from the
class view to keep the changed routing path readable.

### R-01 [P1]: initial outer-factory defect, fixed in fa2c036

Location: [MegaAwareObjectStoreFactory.java, line 28](https://github.com/ursusandwolfcom/task-management-app/blob/7cad968c4922bd761a281e8276c54021c449fa66/src/main/java/com/example/taskmanagement/attachment/application/MegaAwareObjectStoreFactory.java#L28).
Wiring: [AttachmentStorageConfiguration.java, line 69](https://github.com/ursusandwolfcom/task-management-app/blob/7cad968c4922bd761a281e8276c54021c449fa66/src/main/java/com/example/taskmanagement/attachment/application/AttachmentStorageConfiguration.java#L69).

In the original 7cad968 snapshot, the non-MEGA branch delegated to legacy.forUser(userId). It did not
pass externalId to DefaultAttachmentObjectStoreFactory.forAttachment. This is an existing
wrapper defect that made the new S56 routing acceptance criterion incomplete before the hotfix.

Concrete trigger: an attachment has a Dropbox externalId and storage is configured as
local. The wrapper returns LocalAttachmentObjectStore instead of letting the default
factory reject cloud access. Local delete uses Files.deleteIfExists and may succeed when
the local key is absent; AttachmentService can then commit deletion of metadata while
the remote bytes remain. Old local files can also be routed through the current personal
Dropbox destination after linking, bypassing the intended legacy-local rule.

A short Java 21 probe executed the actual compiled wrapper/default/local classes on this
head with synthetic references only. It observed forUser=2 / forAttachment=0 for two
non-MEGA lookups, then selected the local store for a synthetic cloud reference and
successfully called delete without any cloud client. Evidence is retained locally in
.session/verification/s56-routing-review-probe.log. No real provider operation occurred.

The existing DefaultAttachmentObjectStoreFactoryTest directly constructs the inner
factory; it therefore passes while missing this outer dispatch. The deletion integration
test uses Dropbox enabled and unlinked users, which does not exercise the disabled-cloud
or previously-local/personal-link cases.

Implemented correction in fa2c036: delegate the
non-MEGA branch to legacy.forAttachment(userId, externalId). Added wrapper regressions
and real HTTP/Spring-wiring cases; the MEGA branch is preserved. The pre-fix run had seven tests with four failures and zero errors/skips. The post-fix focused run passed all 15 tests, including Dropbox and MEGA integration. Full exact-head/merge CI must pass for the updated candidate.

### Deletion transaction to review

```text
AttachmentService.delete [database transaction]
    |
    +--> find project -> lock project -> reload -> authorize uploader/owner/admin
    +--> record deletion activity
    +--> delete attachment metadata -> flush (not commit)
    +--> factory.forAttachment(uploaderId, externalId) [reference-aware dispatch fixed]
    +--> selected store.delete(key, externalId)
    |       |
    |       +-- authoritative error --> unchecked exception --> DB rollback
    |       +-- Dropbox not-found ---------------------------> idempotent success
    |       +-- confirmed primary delete -------------------> normal return
    +--> commit metadata/activity
```

Review exception propagation through every adapter. DualAttachmentObjectStore suppresses
only secondary local cleanup after successful remote deletion; it must not suppress an
authoritative local/remote failure. SdkDropboxFileClient accepts only path_lookup/not_found
as idempotent deletion; other SDK errors become sanitized STORAGE_ERROR (HTTP 500).

This is not a distributed transaction. If the provider deletes bytes but the response
is lost or the DB commit fails, DB metadata may remain while remote bytes are absent.
A retry can reconcile confirmed not-found; database rollback cannot restore remote bytes.
The project lock is held during provider I/O, which is a latency/lock-duration tradeoff.

## 2. Personal credential classes

```text
+------------------------+       +-----------------------------------+
| DropboxController      |------>| <<interface>> DropboxLinkOperations|
| /api/dropbox           |       +-----------------^-----------------+
+------------------------+                         : implements
                                +-----------------------------------+
                                | DropboxLinkService [changed]      |
                                | + beginAuthorize(userId)          |
                                | + completeAuthorize(id,code,state)|
                                | + status(id), unlink(id)          |
                                +----------+------------------------+
                                           | accounts
            DropboxOAuthClient <-----------+ (separate oauth dependency)
                                           v
+-------------------------------------------------------------------+
| <<interface>> DropboxAccountOperations [changed]                    |
| + credentialStorageConfigured(): boolean                           |
| + link(userId, refreshToken, accountId)                             |
| + credential(userId): Optional<DropboxCredential>                   |
| + status(userId), unlink(userId)                                   |
+----------------------------------^--------------------------------+
                                   : implements
                    +---------------------------------------+
                    | DropboxAccountService [changed]       |
                    | @Transactional; Clock injected        |
                    +-------------------+-------------------+
                                        |
                  +---------------------+-----------------------+
                  | cipher                                      | users
                  v                                             v
+--------------------------------------+   +--------------------------------+
| DropboxCredentialCipher [new]        |   | UserRepository [context]       |
| - key: SecretKeySpec                 |   | JpaRepository<UserEntity,Long> |
| - random: SecureRandom               |   +----------------+---------------+
| + configured(): boolean             |                    | persists
| + encrypt(userId, value): String     |                    v
| + decrypt(userId, value): String     |   +--------------------------------+
+--------------------------------------+   | UserEntity [changed]           |
  AES-256-GCM; 12-byte nonce;              | - encryptedDropboxRefreshToken |
  128-bit tag; AAD binds user;             | - dropboxAccountId             |
  v1:Base64(nonce || ciphertext/tag)       | - dropboxLinkedAt              |
                                          | - version: long [@Version]     |
IdentityConfiguration --creates--> cipher +--------------------------------+
```

Review encryption before save and decryption only at the credential boundary. Plaintext
necessarily exists in memory for OAuth/SDK use; the at-rest guarantee does not mean zero
plaintext in process memory. DropboxCredential.toString redacts its token. The existing
default-factory cache uses plaintext refresh tokens as keys; retention/eviction deserves
separate scrutiny and is not solved by database encryption.

Check separate deployment-key configuration, unique nonces, owner AAD, malformed input,
wrong key/user and authentication failure. Credential read without a key maps to HTTP
503 DROPBOX_CREDENTIALS_NOT_CONFIGURED; corruption/wrong key maps to HTTP 409
DROPBOX_CREDENTIALS_UNAVAILABLE. Personal authorize/callback instead fails the existing
OAuth configuration check (HTTP 400 OAUTH_NOT_CONFIGURED) when storage is unconfigured.
A malformed nonempty configured key fails bean construction; it is not a runtime 503.

### Migration 023

```text
users.dropbox_refresh_token [plaintext]
          |
          +--> add dropbox_encrypted_refresh_token VARCHAR(4096), initially NULL
          +--> clear old accountId/linkedAt; increment affected user versions
          +--> DROP old plaintext column

attachment rows / storage keys / provider references / remote bytes: unchanged
next personal use: reconnect the intended original Dropbox account
```

The user selected relinking, not plaintext-to-ciphertext migration. Review the affected
rows predicate and version increment. Applied migration 020 is unchanged; tracking stays
in public, application tables in task_management, and ddl-auto=validate remains enabled.
Rollback creates an empty old column and clears new links; it cannot restore erased tokens.
Review coordinated application/schema rollback and key-loss/rotation recovery. Dropping
a column is not a claim that backups or historical storage pages contain no old plaintext.

## 3. User-route compatibility and security

```text
SecurityConfiguration [changed]
       +--creates/configures--> SecurityFilterChain
       |                         matcher: /api/** and /users/**
       +--uses---------------> IdentityJwtConverter --> IdentityOperations

HTTP /api/users/* or /users/*
       -> JWT/header or browser-cookie authentication
       -> browser CSRF checks for protected cookie mutations
       -> UserController [changed]
                 +--> IdentityOperations [existing profile/role/version rules]

OpenApiConfiguration / application-alpha.properties [changed]
       -> both route families visible in the appropriate identity specification
```

The bottom portion is request flow, not Java inheritance. Review matcher ordering before
the permissive fallback chain, equal alias behavior, current database role checks and
expected versions. The new alias must not bypass browser CSRF or last-admin protection.
Alpha defaults remain disabled outside the alpha profile.

## 4. Suggested reading order and evidence

1. AttachmentStorageConfiguration and MegaAwareObjectStoreFactory: verify real composition
   first, and verify the R-01 fix through the outer factory and configured HTTP tests.
2. AttachmentService.delete, DefaultAttachmentObjectStoreFactory, DualAttachmentObjectStore,
   DropboxAttachmentObjectStore and SdkDropboxFileClient: follow one failure end to end.
3. DropboxCredentialCipher, DropboxAccountService, UserEntity and migration 023: follow
   plaintext through encryption, persistence, decryption and explicit relinking.
4. DropboxLinkService, IdentityConfiguration, ApiExceptionHandler and Compose/env sample:
   inspect key configuration and public error boundaries without opening .env.
5. UserController, SecurityConfiguration and OpenAPI: compare the two route families.
6. Read regressions alongside each path, then add the missing outer-wiring cases.

| Area | Existing automated evidence | Review boundary |
|---|---|---|
| Provider errors / retry | SdkDropboxFileClientTest, DualAttachmentObjectStoreTest, DropboxDeletionIntegrationTest | SDK failures and metadata/history rollback; not real-provider acceptance |
| Persisted-reference selection | DefaultAttachmentObjectStoreFactoryTest, MegaAwareObjectStoreFactoryTest, AttachmentIntegrationTest | New outer-wrapper and configured HTTP tests cover R-01, local-copy preservation and disabled-cloud rejection |
| Cipher / API / migration | DropboxCredentialCipherTest, DropboxLinkIntegrationTest, DropboxLinkConfigurationTest, DropboxCredentialMigrationTest | Actual migration fixture uses H2; Spring integration/schema checks also run on PostgreSQL |
| Route aliases | IdentityIntegrationTest, BrowserSessionIntegrationTest, OpenApiTest | JWT, live permissions, versions and cookie-CSRF behavior |

Baseline CI before the hotfix passed: exact head 42069283407 and proposed merge
42069293383. Each runs 361 tests per H2/PostgreSQL pass with no errors/failures/skips,
coverage gates, packaged/container restart and 28 browser checks. Proposed merge 57cdb737
and the head share tree ccee9d85 against dev 295fd9b.

Local Windows Java 21.0.12.1/UTC clean verify passed: 361 total, 359 executed, two existing
Unix-process tests skipped by platform assumptions and executed in Linux CI; zero
failures/errors, Checkstyle and coverage passed. These green runs predate discovery of
R-01; the initial routing probe demonstrated that coverage gap. Hotfix fa2c036 and the
new outer-factory/HTTP regressions address it; fresh full verification is required.

Review acceptance checklist:

- [x] Fix R-01 and cover the outer wrapper plus real configured factory wiring (fa2c036, focused tests pass).
- [ ] Trace provider failures to database rollback and document remote/DB atomicity limits.
- [ ] Confirm the irreversible relink/key-recovery behavior is acceptable for deployment.
- [ ] Verify aliases retain identical authentication, live-role, version and CSRF checks.
- [ ] After any fix, repeat required checks on the new exact head and proposed merge.
- [ ] Obtain the required external approval; none has been recorded for this head.

The hotfix changes only the non-MEGA delegation; no live provider operation or deployment occurred. Fresh verification of the updated candidate is available in PR #99 checks; baseline successes above must not substitute for it.
Main/dev remain 295fd9b. Reminders, unattended delivery, AWS deployment, measured capacity
and real-account acceptance are separate planned work; they are not delivered by S56.
