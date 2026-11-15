# TRACMIO v16.13 — Engineering Master Document (EMD) & Product Requirements Document (PRD)

> **Purpose of This Document**
> This combined **Product Requirements Document (PRD)** and **Engineering Master Document (EMD)** is a complete, end-to-end specification to build, clone, audit, or recreate **Tracmio v16.13** from scratch in any repository or hand off to an engineering team / AI coding agent. Every architectural boundary, Room database schema, cryptographic invariant, financial formula, Liquid Glass design token, screen layout, and test requirement is specified below.

---

## 1. Product Overview & Core Philosophy

- **App Name**: `Tracmio`
- **Tagline**: Finance & Expenses Tracker
- **Application ID & Namespace**: `com.tracmio.app`
- **Version**: `versionName = "16.13"`, `versionCode = 30`
- **SDK Targets**: `minSdk = 26` (Android 8.0), `targetSdk = 35` (Android 15), `compileSdk = 35`, Java/Kotlin JVM Target `17`
- **Core Architecture**: 100% Offline-First, Pure Native Kotlin + Jetpack Compose (`Zero WebView`, `Zero Network Permissions`).
- **Visual Design Identity**: **Aurora Teal Liquid Glass (v2.5.0)** — Apple-inspired translucent frosted cards, specular glass borders, 3D glass icon badges, 11 theme presets, 10 procedural 3D mesh wallpapers, custom image wallpaper support, `+1 / -1` UI scaling (`0.85x` to `1.15x`), and real-time `SensorManager` phone-tilt parallax.

### 1.1 Non-Negotiable Privacy & Security Guarantees
1. **Zero Network Access**: `AndroidManifest.xml` MUST NOT declare `android.permission.INTERNET` or `android.permission.ACCESS_NETWORK_STATE`.
2. **Zero Broad Storage Permissions**: Never request `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`. Use Android Photo Picker (`ActivityResultContracts.PickVisualMedia`), `FileProvider`-backed camera capture, and Storage Access Framework (`CreateDocument` / `OpenDocument`).
3. **Fail-Closed Hardware-Backed Encryption**:
   - All structured ledger data is stored in **Room + SQLCipher (`net.zetetic:sqlcipher-android:4.6.1`)** encrypted with a 256-bit random passphrase wrapped by **Android Keystore (`EncryptedSharedPreferences`)**.
   - If SQLCipher native binaries or Android Keystore fail to initialize, the app **MUST fail closed** and render a dedicated recovery screen (`vault_security_error_overlay`) with retry and encrypted backup restore options—never falling back to plaintext or silently deleting existing database files.

---

## 2. Technology Stack & Gradle Configuration

### 2.1 `settings.gradle.kts`
- `rootProject.name = "Tracmio"`
- `include(":app")`
- Repositories: `google()`, `mavenCentral()`, `https://jitpack.io`

### 2.2 `app/build.gradle.kts` Dependencies & Build Setup
- **Plugins**:
  - `com.android.application`
  - `org.jetbrains.kotlin.android`
  - `org.jetbrains.kotlin.plugin.compose`
  - `com.google.devtools.ksp`
- **Room Schema Export**:
  ```kotlin
  ksp {
      arg("room.schemaLocation", "$projectDir/schemas")
  }
  ```
- **Release Hardening**:
  - `isMinifyEnabled = true`
  - `isShrinkResources = true`
  - Single APK output naming: `Tracmio-${variant.versionName}.apk` (`Tracmio-16.13.apk`)
- **Libraries**:
  - Jetpack Compose BOM `2024.12.01` (`ui`, `foundation`, `material3`, `material-icons-core`, `material-icons-extended`, `ui-tooling-preview`)
  - `androidx.activity:activity-compose:1.10.0`
  - `androidx.fragment:fragment-ktx:1.8.5`
  - `androidx.navigation:navigation-compose:2.8.5`
  - `androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7`, `lifecycle-runtime-ktx:2.8.7`, `lifecycle-runtime-compose:2.8.7`
  - `androidx.room:room-runtime:2.6.1`, `room-ktx:2.6.1`, `ksp("androidx.room:room-compiler:2.6.1")`
  - `net.zetetic:sqlcipher-android:4.6.1`
  - `androidx.sqlite:sqlite-ktx:2.4.0`
  - `androidx.security:security-crypto:1.1.0-alpha06`
  - `androidx.biometric:biometric:1.1.0`
  - **Testing**: `junit:4.13.2`, `kotlinx-coroutines-test:1.9.0`, `org.json:json:20231013`, `androidx.test:core:1.6.1`, `org.robolectric:robolectric:4.14.1`

### 2.3 `AndroidManifest.xml`
- **Permissions**:
  - `android.permission.POST_NOTIFICATIONS`
  - `android.permission.CAMERA` (`android.hardware.camera` with `android:required="false"`)
  - `android.permission.RECEIVE_BOOT_COMPLETED`
- **Components**:
  - `MainActivity` (`exported="true"`, `launchMode="singleTop"`, `windowSoftInputMode="adjustResize"`)
  - `DailyReminderReceiver` (`exported="false"`)
  - `ReminderNotificationActionReceiver` (`exported="false"`)
  - `EventReminderReceiver` (`exported="false"`)
  - `ReminderBootReceiver` (`exported="true"`, receives `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`)
  - `androidx.core.content.FileProvider` (`authorities="${applicationId}.fileprovider"`)

---

## 3. Repository & Package Structure

```text
com.tracmio.app/
├── MainActivity.kt                        # FragmentActivity host, WindowCompat edge-to-edge, SAF/Camera/Biometric launchers
├── DailyReminderReceiver.kt               # Rotating 100-item daily financial reminders on "tracmio_daily" channel
├── EventReminderReceiver.kt               # Custom scheduled bill/event reminder receiver
├── ReminderBootReceiver.kt                # Reschedules alarms after device reboot or app upgrade
├── ReminderNotificationActionReceiver.kt  # Handles "+ Add income" / "+ Add expense" notification quick actions
├── TracmioReminderScheduler.kt            # AlarmManager exact/inexact scheduling helper
├── data/
│   ├── CsvParser.kt                       # RFC-4180 CSV parser & exporter with formula injection neutralization
│   ├── CustomTrackersCalculator.kt        # Custom tracker progress, streak, and delta aggregation
│   ├── FinancialCalculator.kt             # Core account balance, net cashflow, category totals, math expression evaluator
│   ├── LegacyMigrationGuard.kt            # Auto & manual migration from legacy FinTrack/Tracmio DBs and JSON snapshots
│   ├── ModelsAndState.kt                  # Navigation enums, UI state models, theme presets, 24 Tools Hub metadata
│   ├── MoneyPaise.kt                      # Exact Long paise <-> Double rupee conversion, Indian/Intl currency formatting, decoy stealth mode
│   ├── NativeIntelligenceCalculators.kt   # Financial X-Ray (0-100 score + 10 vitals), 50/30/20 Money Story, Spending DNA, Month Comparison
│   ├── NativeLedgerCalculator.kt          # Safe-to-Spend, 3M/6M Emergency Runway, Cash Count, Allocations, Round-Up Jar, Snowball/Avalanche Payoff
│   ├── PreferencesAndThemeStore.kt        # SharedPreferences persistence for theme, Liquid Glass Studio sliders, quick chips, onboarding
│   ├── TransactionDateTime.kt             # Date/time formatting, calendar grid builders, date grouping
│   ├── XlsxExporter.kt                    # Zero-dependency OpenXML (.xlsx) multi-sheet generator with XML/formula escaping
│   ├── local/
│   │   ├── TracmioDatabase.kt             # Room v10 + SQLCipher database, MIGRATION_1_2..9_10, fail-closed openVaultOrFail
│   │   ├── AccountEntity.kt               # accounts table
│   │   ├── TransactionEntity.kt           # transactions table (indexed on type+timestamp, accountId, category)
│   │   ├── BudgetEntity.kt                # budgets table
│   │   ├── GoalEntity.kt                  # goals table
│   │   ├── GoalContributionEntity.kt      # goal_contributions table
│   │   ├── LoanEntity.kt                  # loans table
│   │   ├── LoanPaymentEntity.kt           # loan_payments table
│   │   ├── PersonEntity.kt                # persons table
│   │   ├── CategoryEntity.kt              # categories table
│   │   ├── CustomTrackerEntities.kt       # custom_trackers, tracker_entries, tracker_folders tables
│   │   ├── FeatureEntities.kt             # subscriptions, academic_events, reminders, khata_entries, wishlist_cooldowns, cashback_rewards, price_per_use_items
│   │   ├── AccountDao.kt
│   │   ├── FinanceDao.kt
│   │   ├── CustomTrackerDao.kt
│   │   └── FeatureDao.kt
│   ├── repository/
│   │   └── FinanceRepository.kt           # Single source of truth, transactional mutations, health audit, orphan repair, backup/restore
│   ├── security/
│   │   ├── AppLockManager.kt              # PBKDF2 PIN/Answer/Recovery Key hashing, brute-force lockout, Keystore EncryptedSharedPreferences
│   │   └── TracmioSecurityEngine.kt       # AES-256-GCM + PBKDF2 backup encryption, input sanitization, VaultInitState
│   └── services/
│       ├── NativeBackupService.kt         # Plain/Encrypted JSON export, preview validation, Safe Merge / Clean Replace import
│       └── NativeStatementPdf.kt          # Multi-page PDF statement, custom report, and single-receipt PDF generator + PrintManager bridge
└── ui/nativeapp/
    ├── Theme.kt                           # Aurora Teal & 11 preset color schemes, typography, scaled density
    ├── LiquidGlassComponents.kt           # Frosted glass cards, 3D icon badges, Apple-style capsule sliders, mesh wallpaper canvas, tilt sensor
    ├── NativeTracmioApp.kt                # Top-level Scaffold, Fixed Topbar, Floating Bottom Bar, Vault Error & App Lock overlays
    ├── NativeTracmioViewModel.kt          # Reactive state combinations, input validation, async vault lifecycle, CRUD handlers
    ├── NativeHostCallbacks.kt             # Interface for OS intents (SAF, Camera, Photo Picker, Print, Biometrics, Share)
    ├── NativeHostCallbacksImpl.kt         # Concrete implementation bound to MainActivity launchers
    ├── DashboardAndAccountsScreens.kt     # Home Dashboard, Accounts & Wallets, Allocations, Reconcile, 3-Step Cash Counter
    ├── TransactionsAndAddEditModal.kt     # Date-grouped Ledger, Top-Left Filters, 5-Type Add/Edit Modal, Receipt Viewer
    ├── PlanningAndPeopleScreens.kt        # Budgets, 3M/6M Runway, Goals, Loans & Snowball/Avalanche Planner, People & Smart Split
    ├── ReportsAndIntelligenceScreens.kt   # Interactive Charts, Financial X-Ray, Money Story, Spending DNA, Calendar & No-Spend Streak
    ├── TrackersAndTimeMachineScreens.kt   # Custom Trackers + Folders, 3-Tab Time Machine, Subscriptions, Academic Fee & Reminders
    ├── MoreHubAndToolsScreens.kt          # 24-Tool More Hub (with Organize mode), Khata, Wishlist, Cashback, Price-Per-Use, App Guide
    ├── ToolsAndSettingsScreens.kt         # Liquid Glass UI Studio, App Lock, Data & Backup (with Legacy Wizard), Security & DB Audit
    ├── AccountEditDialog.kt               # Create/Edit/Archive Account & Credit Card cycle modal
    ├── OnboardingDialog.kt                # First-launch currency, theme, and starter wallet setup
    └── TrackerFolderDialog.kt             # Custom tracker workspace folder manager
```

---

## 4. Database Schema (Room Version 10 — 19 Entities)

`TracmioDatabase` (`version = 10`, `exportSchema = true`) defines 19 tables and explicit migrations `MIGRATION_1_2` through `MIGRATION_9_10`:

| # | Table Name | Key Columns & Purpose |
|---|------------|-----------------------|
| 1 | `accounts` | `id`, `name`, `accountType` (`Bank`, `Cash`, `Wallet`, `Credit Card`, `Savings`), `type`, `balance`, `balancePaise`, `creditLimit`, `creditLimitPaise`, `statementDay`, `dueDay`, `allocationsJson`, `accountNumber`, `isArchived`, `colorHex`, `createdAt`, `updatedAt` |
| 2 | `transactions` | `id`, `stableUid` (UUID hex for cross-device deduplication), `accountId`, `type` (`EXPENSE`, `INCOME`, `TRANSFER_OUT`, `TRANSFER_IN`, `BORROW`, `LEND`), `amount`, `amountPaise`, `category`, `subcategory`, `note`, `hashtags`, `timestamp`, `transferAccountId`, `transferId`, `personId`, `paymentMethod`, `upiApp`, `warrantyDate`, `attachmentUri`, `allocationId`. Indexed on `(type, timestamp)`, `(accountId)`, `(category)`. |
| 3 | `budgets` | `id`, `category`, `monthlyLimit`, `limitPaise`, `month` (`yyyy-MM`), `createdAt`. Upsert deduplicates by `(LOWER(category), month)`. |
| 4 | `goals` | `id`, `name`, `title`, `targetAmount`, `targetPaise`, `currentAmount`, `currentPaise`, `deadline`, `icon`, `colorHex`, `createdAt` |
| 5 | `goal_contributions` | `id`, `goalId`, `amount`, `amountPaise`, `note`, `timestamp` |
| 6 | `loans` | `id`, `name`, `lender`, `principal`, `principalPaise`, `interestRate` (APR %), `tenureMonths`, `monthlyPayment`, `emiPaise`, `startDateMillis`, `isClosed` |
| 7 | `loan_payments` | `id`, `loanId`, `amount`, `amountPaise`, `note`, `timestamp` |
| 8 | `persons` | `id`, `name`, `phone`, `upiId`, `netBalance`, `netBalancePaise`, `notes`, `createdAt` |
| 9 | `categories` | `id`, `name`, `type` (`EXPENSE` / `INCOME`), `icon`, `colorHex` |
| 10 | `custom_trackers` | `id`, `title`, `trackerType`, `targetValue`, `currentValue`, `unit`, `icon`, `colorHex`, `folderId`, `isPinned`, `updatedAt` |
| 11 | `tracker_entries` | `id`, `trackerId`, `deltaValue`, `note`, `attachmentPath`, `timestamp` |
| 12 | `tracker_folders` | `id`, `folderName`, `icon`, `colorHex`, `sortOrder` |
| 13 | `subscriptions` | `id`, `name`, `amount`, `amountPaise`, `billingCycle`, `nextDueDateMillis`, `category`, `isSimOrWifi`, `validityDays`, `isActive` |
| 14 | `academic_events` | `id`, `title`, `feeAmount`, `paidAmount`, `dueDate`, `semester`, `isPaid` |
| 15 | `reminders` | `id`, `title`, `amount`, `dueDate`, `timeHour`, `timeMinute`, `isCompleted`, `isRecurring` |
| 16 | `khata_entries` | `id`, `shopOrFriend`, `itemDescription`, `amount`, `isSettled`, `timestamp` |
| 17 | `wishlist_cooldowns` | `id`, `itemName`, `price`, `createdAtMillis`, `status` (`COOLING`, `APPROVED`, `SKIPPED`, `PURCHASED`) |
| 18 | `cashback_rewards` | `id`, `sourceApp`, `amount`, `note`, `timestamp` |
| 19 | `price_per_use_items` | `id`, `itemName`, `purchasePrice`, `usageCount`, `targetUses`, `createdAtMillis` |

---

## 5. Security & Cryptographic Specification

### 5.1 App Lock (`AppLockManager`)
- **Storage**: `EncryptedSharedPreferences` (`tracmio_app_lock_secure_prefs`) backed by `MasterKey.KeyScheme.AES256_GCM`.
- **Secret Hashing (`TracmioSecurityEngine.hashSecretPbkdf2`)**:
  - Format: `pbkdf2_sha256$<iterations>$<base64_salt_16B>$<base64_hash_32B>`
  - Iterations: `120,000`
  - Constant-time comparison via `MessageDigest.isEqual`.
  - Legacy salted SHA-256 hashes are verified once and transparently re-hashed to PBKDF2.
- **Brute-Force Lockout**:
  - Persists `failed_attempts` and `lockout_until_ms`.
  - Thresholds: `5` failures → `30s` cooldown; `8` failures → `5m` (`300s`) cooldown; `10+` failures → `30m` (`1800s`) cooldown.
  - Lockout countdown ticks every second on the PIN overlay and disables numeric keypad buttons until expiry or biometric unlock.
- **Sensitive Action Re-Authentication**:
  - Disabling or modifying an active App Lock requires entering the current PIN or completing biometric authentication first.
- **One-Time Recovery Key**:
  - Generated on lock setup (`TRAC-XXXX-XXXX`), hashed with PBKDF2 before storage, and displayed **once** in a modal dialog with a copy button and mandatory acknowledgement checkbox.

### 5.2 Encrypted Backups (`TracmioSecurityEngine` & `NativeBackupService`)
- **Envelope Format**:
  ```json
  {
    "tracmioEncrypted": true,
    "version": "16.13",
    "kdf": "PBKDF2WithHmacSHA256",
    "cipher": "AES/GCM/NoPadding",
    "iterations": 120000,
    "salt": "<Base64 16-byte random salt>",
    "iv": "<Base64 12-byte random IV>",
    "ciphertext": "<Base64 AES-256-GCM ciphertext + 128-bit auth tag>"
  }
  ```
- **Restore Modes**:
  1. **Safe Merge (`SAFE_MERGE` — Default)**: Preserves existing local data and merges backup records inside `db.withTransaction`, deduplicating transactions by `stableUid` and accounts/budgets/goals/loans/persons/trackers by natural keys while remapping foreign keys.
  2. **Clean Replace (`CLEAN_REPLACE`)**: Validates the entire backup payload in memory first, then clears and replaces all 19 tables atomically inside a single Room transaction.
- **Attachment Sanitization**:
  - Rejects path traversal (`..`, absolute paths) and verifies image magic bytes (`PNG`, `JPEG`, `WEBP`) before writing into `filesDir/attachments` or `filesDir/wallpapers`.

---

## 6. Financial Formulas & Calculators (`MoneyPaise` & `NativeLedgerCalculator`)

1. **Integer Paise Arithmetic (`MoneyPaise`)**:
   - All monetary sums convert rupees to `Long` paise (`Math.round(amount * 100.0)`) to prevent IEEE-754 floating-point drift.
   - Rejects `NaN`, `Infinity`, and negative numbers on transaction/budget/goal/loan inputs.
2. **Account Live Balance (`FinancialCalculator.balance`)**:
   - `openingBalancePaise + sum(INCOME, TRANSFER_IN, BORROW) - sum(EXPENSE, TRANSFER_OUT, LEND)` for that `accountId`.
   - Paired transfers insert both `TRANSFER_OUT` (source account) and `TRANSFER_IN` (destination account) sharing a `transferId`; deleting or editing either side updates/deletes both atomically.
   - Deleting an account cascades deletion to its transactions and paired transfers, and reverses allocation adjustments.
3. **Safe-to-Spend Daily Allowance**:
   - `unallocatedBalance = max(0, totalLiquidBankCashWalletBalance - totalAccountAllocations)`
   - `dailyAllowance = unallocatedBalance / remainingDaysInCurrentMonth`
4. **Emergency Fund Runway (`3M` / `6M` Rolling Window)**:
   - `liquidSavings = sum(liveBalance of Bank/Cash/Wallet/Savings accounts) - sum(abs(negativeBalance) of Credit Card accounts)`
   - Filters `EXPENSE` transactions within the rolling `3` or `6` calendar months (strictly excluding `INCOME`, `TRANSFER_IN`, `TRANSFER_OUT`, `BORROW`, `LEND`).
   - `averageMonthlyExpense = totalWindowExpenses / activeWindowSpanMonths`
   - `runwayMonths = liquidSavings / averageMonthlyExpense` (or `12.0` if expenses are `0` and `liquidSavings > 0`).
5. **Snowball vs. Avalanche Debt Payoff (`simulateDebtPayoff`)**:
   - Excludes closed loans (`isClosed == true`) and paid-off loans (`remainingPaise <= 0`).
   - **Avalanche** sorts active loans by highest `interestRate` (APR) first, then lowest balance.
   - **Snowball** sorts active loans by lowest `remainingBalancePaise` first, then highest APR.
   - Each simulated month:
     1. Accrues monthly interest (`round(balancePaise * (apr / 100 / 12))`) on all active loans.
     2. Pays each active loan's minimum EMI.
     3. Rolls over `extraMonthlyPayment` **plus any freed minimum payments from paid-off loans** (`totalMonthlyBudgetPaise - spentThisMonthPaise`) onto the highest-priority target loan.
     4. Flags `nonAmortizing = true` if total balance fails to decrease after 12 months or hits the 600-month cap.
6. **3-Step Cash Denomination Counter**:
   - Notes: `₹500, ₹200, ₹100, ₹50, ₹20, ₹10`
   - Coins: `₹20, ₹10, ₹5, ₹2, ₹1`
   - Calculates exact physical cash total and optionally logs a reconciliation adjustment transaction.

---

## 7. Complete UI/UX & Feature Specification

### 7.1 Fixed Topbar & Navigation Architecture
- **Fixed Topbar**:
  - Left-aligned 3D Aurora Teal **Tracmio** emblem + title across all screens (no topbar search icon).
  - Right-side action pills: Currency selector (`₹ / $ / € / £`), Privacy Eye toggle (`₹••••••`) with **Triple-Tap Stealth Decoy Mode** (renders deterministic lower decoy balances), Notification/Reminder bell, and Quick Settings trigger.
- **Floating Liquid Glass Bottom Navigation**:
  - `Home` (Dashboard)
  - `Accounts` (Wallets, Allocations, Cash Count, Reconcile, Credit Cards)
  - Center primary `+` FAB (opens 5-Type Transaction Modal)
  - `Ledger` (Transactions, Filters, Hashtags, Warranties, Exports)
  - `Reports` (Charts, X-Ray, Money Story, DNA, Calendar)
  - `More` (24-Tool Hub, Organize Mode, App Guide, Settings)

### 7.2 All 24 Tools in the More Hub (with `⇄ Organize` Pin/Reorder Mode & App Guide `Open →` Links)
1. **Budgets & Runway** (`3M` / `6M` toggle, category progress bars)
2. **Savings Goals** (target ring, deadline, `+ Contribute` top-ups)
3. **Loans & Debt Payoff** (EMI tracker + Snowball vs. Avalanche simulator with non-amortizing alert)
4. **People & Splits** (Lend/Borrow ledger, `1-Tap Settle Up`, Smart Split calculator & WhatsApp summary share)
5. **Custom Trackers** (numeric/habit trackers with Folder workspaces, pin to dashboard, delta logs)
6. **Subscriptions & Recharges** (recurring bills, SIM/Wi-Fi validity countdown, `1-Tap Pay & Log`)
7. **24h Cooling Wishlist** (impulse-buy timer with `Buy Now` -> logs expense or `Skip & Save`)
8. **Khata Book** (local shop/friend informal tab tracker with settle toggle)
9. **Cashback & Rewards** (tracks UPI/card scratch cards & cashback earned)
10. **Price-Per-Use Value Tracker** (tracks purchase cost divided by usage count toward target uses)
11. **Round-Up Spare Change Jar** (computes virtual spare change from daily expenses)
12. **3-Step Cash Counter** (denomination tally for notes & coins)
13. **Account Reconcile** (compares actual bank/cash balance vs. ledger balance)
14. **Warranty & Expiry Vault** (filters transactions with active warranty/deadline dates)
15. **Academic & Exam Fee Planner** (semester fee schedule & paid status tracker)
16. **Bill & Custom Reminders** (daily rotating notifications + custom scheduled alarms)
17. **Financial X-Ray** (`0–100` composite health score ring + 10 financial vitals)
18. **Money Story (`50/30/20`)** (needs/wants/savings breakdown, top category, top payment rail)
19. **Spending DNA & Weekend Radar** (`Mon–Sun` weekday vs. weekend spending intensity)
20. **Time Machine (`3 Tabs`)** (Historical lookup, Future balance forecast, Side-by-side Date Comparison)
21. **Interactive Calendar & No-Spend Streak** (7-column monthly heatmap + streak milestone badges)
22. **Liquid Glass UI Studio** (11 themes, 10 3D mesh wallpapers, custom photo wallpaper, blur/opacity/brightness capsule sliders, `+1/-1` UI scale, tilt parallax toggle)
23. **App Lock & Biometrics** (4/6-digit Liquid Glass PIN pad, biometric toggle, security question, one-time recovery key)
24. **Data, Backup, Exports & DB Audit** (Plain/AES-256-GCM JSON backup, Safe Merge/Clean Replace restore preview, Legacy JSON/SQLite Migration Wizard, `.xlsx`/CSV/PDF exports, SQLite Integrity & Orphan Repair)

---

## 8. Export & Print Specifications

- **Multi-Page PDF Statements (`NativeStatementPdf`)**:
  - Paginates all transactions (`MAX_LINES_PER_PAGE = 38`) so large ledgers are never truncated to a single page.
  - Every page includes header metadata and footer: `"Tracmio Offline Vault (v16.13) · Page X of Y"`.
  - Supports Single-Transaction Expense Receipt PDF and PrintManager integration.
- **CSV & Excel (`.xlsx`) Formula Neutralization (`CsvParser` & `XlsxExporter`)**:
  - Any user text starting with `=`, `+`, `-`, `@`, `\t`, or `\r` (ignoring leading spaces) is prefixed with `'` (`sanitizeFormulaCell`) in both CSV and `.xlsx` cells to prevent spreadsheet formula injection (`DDE` / `CMD` execution).
  - `.xlsx` workbooks contain two sheets (`Transactions` and `Accounts`) with valid OpenXML relationships and XML entity escaping (`&apos;`, `&amp;`, `&lt;`, `&gt;`, `&quot;`).

---

## 9. Verification & CI/CD Pipeline Requirements

### 9.1 Required Automated Test Suites (`app/src/test/java/com/tracmio/app/data/`)
1. **`TracmioSecurityEngineTest`** (4 tests):
   - Salted PBKDF2 secret hashing & legacy SHA-256 verification.
   - AES-256-GCM backup encrypt/decrypt roundtrip.
   - Wrong-password, tampered ciphertext, and low-iteration (`< 100,000`) rejection.
   - XSS/script tag and control-character input sanitization.
2. **`TracmioVaultRobolectricTest`** (4 tests):
   - 100-item `notifications.json` catalog validation.
   - `AppLockManager` enable, PIN verification, and recovery key flow.
   - Full 19-entity + attachment backup export, **Safe Merge** deduplication, and **Clean Replace** restore, plus path-traversal and invalid-image rejection.
   - Monthly backup preferences and `tracmio_daily` notification channel creation.
3. **`FinancialCalculatorTest`** (8 tests):
   - Live account balance, net cashflow, and math expression evaluator (`"120 + 45 + 30"`, `"₹300 / 2"`).
   - Safe-to-Spend daily allowance and unallocated balance calculation.
   - Emergency Runway `3M` and `6M` rolling window calculation (excluding income, transfers, and loans).
   - Cash denomination counter, Round-Up spare change, and Snowball vs. Avalanche debt payoff ordering, interest comparison, closed-loan exclusion, and non-amortizing detection.
   - `MoneyPaise` conversions, `NaN`/`Infinity` guards, privacy mask, decoy stealth mode, and CSV/XLSX roundtrips.
   - Monthly pace meter, month-over-month comparison, no-spend streak, and Money Story.
   - Daily/Weekly/Monthly/Yearly spending distribution windows.
   - Account allocation spend deduction and reversal.
4. **`TracmioAuditRemediationRobolectricTest`** (7 tests):
   - Fail-closed `TracmioDatabase.openVaultOrFail` when SQLCipher or Android Keystore fails, plus non-destructive legacy key migration.
   - Real SQLite v1 → v10 Room migration upgrade preserving existing accounts and transactions and creating all 19 tables.
   - Real SQLite corruption injection (orphaned transactions, duplicate `stableUid`, negative amounts), health check detection, and `repairOrphanRecords()` verification.
   - `AppLockManager` brute-force lockout (`5` failures → `30s` cooldown), biometric reset, legacy SHA-256 -> PBKDF2 upgrade, and Keystore fail-closed behavior.
   - Budget upsert deduplication by `(category, month)` and cascading account deletion.
   - `LegacyMigrationGuard` import from legacy JSON and real legacy SQLite `.db` file.
   - Multi-page PDF statement pagination (`65` transactions across `3+` pages) and CSV/XLSX formula injection neutralization.

### 9.2 Verification Command
```bash
./gradlew --no-daemon --stacktrace testDebugUnitTest lintDebug assembleDebug compileReleaseKotlin
```
All 23 unit/Robolectric tests, Android Lint, Release Kotlin compilation, and `Tracmio-16.13.apk` packaging must pass with zero errors.
