# Architecture: Sigenergy Battery Monitor

A two-component home-automation system that watches the state of charge (SOC) of a
home battery (Sigenergy SigenStor), fires an alert and/or controls crypto miners when
the SOC crosses a user-configured threshold, and keeps the user informed via an
Android push notification.

- **Android app** (`SigenergyBattery`) — a thin remote control + display. Configure
  the trigger, view live SOC/history, receive push alerts. **Runs no background
  polling.**
- **Hermes Bridge** (`hermes-bridge`) — an always-on Python/FastAPI service on the
  LAN. **Owns all scheduling**, threshold evaluation, miner actions, and FCM push.

> Rationale: previously the Android app ran a foreground service that polled every
> 5 minutes. Device power management stretched those polls to ~20 minutes. Scheduling
> now lives on the always-on bridge, which polls reliably and has direct access to the
> battery (Modbus), Home Assistant, and the miners. The phone costs zero battery.

---

## System topology

```
┌─ Sigenergy Battery app (Android) ──────────────────────────────┐
│  Compose UI · Retrofit client · FCM receiver (no service)      │
│                                                                │
│  POST /api/trigger   (arm monitoring)                          │
│  GET  /api/trigger   (sync status when opened)                 │
│  DELETE /api/trigger (stop / disconnect)                       │
│  POST /api/device    (register FCM token)                      │
│  GET  /api/solar/now | /api/solar/history (live display)       │
└───────────────┬───────────────────────────────────────┬────────┘
        HTTP 8500 (LAN)                        FCM push (internet)
                │                                   │
┌───────────────▼───────────────────────────────────▼────────────┐
│ Hermes Bridge (docker on 10.0.0.30, uvicorn on :8500)          │
│  FastAPI · background trigger worker thread · firebase-admin    │
│  data/: trigger.json, devices.json                              │
│                                                                │
│  Reads SOC via Modbus TCP  ─► Sigenergy SigenStor (10.0.0.25)   │
│  Turns miner switches via  ─► Home Assistant (10.0.0.151:8123)  │
│  Sets power preset via     ─► Antminer GraphQL (10.0.0.35:80)   │
└─────────────────────────────────────────────────────────────────┘
```

**Network facts**
- Bridge: `10.0.0.30:8500` (docker host, stack at `/opt/stacks/hermes-bridge/`).
- Home Assistant: `10.0.0.151:8123` (token via env).
- Sigenergy Modbus: `10.0.0.25:502` (EMS slave **247**).
- Antminer GraphQL: `10.0.0.35:80` (auth `root:root`).
- Firebase project: `sigminer-ed92f` (both the app's `google-services.json` and the
  bridge's service-account key must belong to this project).

---

## Repositories & deployment

| Component | Location | Remote |
|---|---|---|
| Android app | `~/Coding/Android/SigenergyBattery` (git `main`) | (local) |
| Hermes Bridge | cloned at `/opt/stacks/hermes-bridge/` on docker | `ssh://git@docker.lan:222/diarmaid/hermes-bridge.git` (Gitea) |

**Bridge deploy** (from a local clone):
```bash
scp main.py requirements.txt compose.yaml docker:/opt/stacks/hermes-bridge/
ssh docker 'cd /opt/stacks/hermes-bridge && docker compose up -d --build'
```
The compose file bind-mounts `./data:/app/data` and
`./firebase-service-account.json:/run/secrets/firebase-service-account.json:ro`.

**App build/install:**
```bash
./gradlew :app:assembleDebug          # or :app:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Secrets required at build time: `secrets.properties` (`HERMES_API_KEY`) and
`app/google-services.json`. Both are gitignored-safe (secrets.properties is
gitignored; google-services.json is committed).

---

## Hermes Bridge (`hermes-bridge/main.py`)

Python 3.11 / FastAPI 0.111 / uvicorn. Single-file service (~1000 lines) plus
`tests/`. Deps: `fastapi`, `uvicorn[standard]`, `requests`, `firebase-admin`
(`requirements.txt`); dev deps in `requirements-dev.txt` (`pytest`, `httpx`).

### Configuration (env vars, `.env` on docker)

| Env | Default | Purpose |
|---|---|---|
| `HERMES_API_KEY` | — | Bearer token required by every route |
| `HA_URL` / `HA_TOKEN` | `http://10.0.0.151:8123` / — | Home Assistant REST |
| `SIGENERGY_HOST` / `SIGENERGY_PORT` | `10.0.0.25` / `502` | Modbus TCP |
| `MINER_HOST` / `MINER_PORT` | `10.0.0.35` / `80` | Antminer GraphQL |
| `FORECAST_*` | (solar forecast) | forecast.solar params |
| `VERIFY_*` | `/app/data/...` | forecast verification log/state |
| `TRIGGER_STATE` / `DEVICES_STATE` | `/app/data/trigger.json` / `devices.json` | persistence |
| `GOOGLE_APPLICATION_CREDENTIALS` | — | FCM service-account path (compose sets it) |
| `FCM_PROJECT_ID` | (from service account) | optional sender-project override |

### API surface (all require `Authorization: Bearer <HERMES_API_KEY>`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | liveness |
| GET | `/api/solar/now` | live PV/grid/battery/SOC + HA enrichment |
| GET | `/api/solar/history` | 24 h SOC series (5-min samples, 5-min cache) |
| GET | `/api/solar/forecast` | forecast.solar curve/totals |
| GET | `/api/solar/verification` | forecast-vs-actual tracking |
| POST | `/api/miner/on` | turn on all miner switches (HA) |
| POST | `/api/miner/off` | turn off all miner switches (HA) |
| GET | `/api/miner/status` | switch states + bosminer telemetry + power target |
| POST | `/api/miner/power-preset/{preset}` | apply `low`/`efficient`/`max` GraphQL preset |
| **POST** | **`/api/trigger`** | arm/re-arm a SOC trigger (validates + persists) |
| **GET** | **`/api/trigger`** | current trigger config + runtime status |
| **DELETE** | **`/api/trigger`** | disable the trigger |
| **POST** | **`/api/strategy`** | start/replace a strategy (cancels any trigger) |
| **GET** | **`/api/strategy`** | running strategy config + runtime status |
| **DELETE** | **`/api/strategy`** | stop the strategy |
| **GET** | **`/api/strategy/templates`** | seasonal templates (summer/spring/autumn) |
| **POST** | **`/api/device`** | register an Android FCM token |

### Trigger engine (the core of this feature)

**Persistence** — `trigger.json` (JSON, guarded by `_trigger_lock`). Schema:

```json
{
  "enabled": true,
  "interval_minutes": 5,
  "threshold_soc": 95.0,
  "direction": "AT_OR_BELOW",
  "actions": ["NOTIFY", "MINER_ON", "SET_POWER_PRESET"],
  "miner_preset": "low",
  "created_at": 1785939827.0,
  "fired": false,
  "fired_at": null,
  "fired_soc": null,
  "action_error": null,
  "last_checked_at": 1785942239.0,
  "last_soc": 100.0
}
```

**Scheduler** — a single daemon thread (`trigger_worker`) started from the FastAPI
`lifespan`. Loop: call `check_trigger_once()`; if it fired, run actions then push;
then `_trigger_wake.wait(interval*60)`. `POST`/`DELETE /api/trigger` set
`_trigger_wake` so re-arming is checked **immediately** rather than at the next
sleep boundary.

**Firing flow** (`check_trigger_once`):
1. Load config under lock; bail if not enabled or already fired.
2. `read_soc()` — Modbus input register `30014` on slave 247, value × 0.1.
3. `trigger_condition_met(soc, threshold, direction)` — `>=` or `<=` depending on
   direction.
4. On match: set `fired=true`, `fired_at`, `fired_soc`, persist, and return so the
   worker can run actions **outside** the lock (the preset can block ~2 min).

**Actions** (`execute_trigger_actions`) — idempotent, mirrors the app's rules:
- `MINER_ON`/`MINER_OFF`: only call HA `switch/turn_on|off` if
  `should_toggle_miner()` says state differs.
- `SET_POWER_PRESET`: only call GraphQL if `should_set_preset()` says the current
  autotuning power target differs; waits `PRESET_DELAY_SECONDS` (120) after turning
  the miner on so it can boot.
- Any failure is stored in `action_error` and surfaced in the push body / status.

**One-shot semantics:** once `fired=true` the worker stops evaluating until the app
POSTs `/api/trigger` again (re-arm resets `fired`).

### Strategy engine (automated day-long mining)

A strategy is a looping state machine that ramps the miner up as the battery
charges, winds it down as the evening draws in, and shuts it off when the
battery depletes — running until stopped. It is **mutually exclusive** with the
one-shot trigger: starting one disables the other (both POST handlers cancel
the opposite system and wake both workers).

**Persistence** — `strategy.json` (JSON, guarded by `_strategy_lock`). Schema:

```json
{
  "enabled": true,
  "name": "Summer (Jun-Aug)",
  "interval_minutes": 5,
  "active_hours_start": "06:00",
  "active_hours_end": "22:00",
  "steps": [
    {"name": "Idle", "condition": {"soc_threshold": 70, "direction": "AT_OR_BELOW"}, "actions": ["MINER_OFF"]},
    {"name": "Ramp Up", "condition": {"soc_threshold": 80, "direction": "AT_OR_ABOVE"}, "actions": ["MINER_ON", "SET_POWER_PRESET"], "miner_preset": "low"},
    {"name": "Full Power", "condition": {"soc_threshold": 90, "direction": "AT_OR_ABOVE"}, "actions": ["SET_POWER_PRESET"], "miner_preset": "max"},
    {"name": "Winding Down", "condition": {"soc_threshold": 80, "direction": "AT_OR_BELOW", "time_after": "16:00"}, "actions": ["SET_POWER_PRESET"], "miner_preset": "low"}
  ],
  "current_step": 0,
  "previous_step": null,
  "last_transition_at": null,
  "last_transition_reason": null,
  "last_transition_soc": null,
  "last_error": null,
  "last_checked_at": null,
  "last_soc": null
}
```

**Evaluation** (`check_strategy_once`, `strategy_worker`) — every `interval_minutes`:
1. Bail if disabled; read SOC; record `last_soc`.
2. **Active-hours gate** — outside the window the engine pauses; at the end of
   the window it forces the miner OFF via the OFF step.
3. **Priority classification** (`strategy_target_step`) — OFF steps are
   evaluated first (never drain the battery), then remaining steps in reverse
   list order so the most advanced matching state wins (e.g. Full Power beats
   Ramp Up at SOC ≥ 90).
4. On a transition, the engine persists `current_step` and returns a **chain**
   (all steps traversed forward, wrapping). The worker merges the chain's
   actions (`MINER_ON` runs once, the final preset wins, the boot delay applies
   once) and pushes an FCM update. This makes SOC jumps skip intermediate steps
   without ever leaving the miner off or at the wrong preset.

**Templates** — `GET /api/strategy/templates` returns three seasonal strategies
for Northern Ireland (server-side so tweaks need no app rebuild):

| | Summer (Jun–Aug) | Spring (Mar–May) | Autumn (Sep–Nov) |
|---|---|---|---|
| Active hours | 06:00–22:00 | 07:00–21:00 | 08:00–20:00 |
| Idle (OFF) | SOC ≤ 70% | SOC ≤ 75% | SOC ≤ 75% |
| Ramp Up (ON, LOW) | SOC ≥ 80% | SOC ≥ 85% | SOC ≥ 90% |
| Full Power (MAX) | SOC ≥ 90% | SOC ≥ 95% | SOC ≥ 98% |
| Winding Down (LOW) | ≥16:00 & SOC ≤ 80% | ≥15:00 & SOC ≤ 80% | ≥14:00 & SOC ≤ 80% |

Spring/autumn are deliberately more conservative (weaker solar): they wait for
a fuller battery before starting and keep more in reserve overnight.

### FCM push (`send_fcm_alert`)

- Reads all tokens from `devices.json` (capped at 20, appended by `POST /api/device`).
- Sends one high-priority message per token via `firebase-admin`:
  ```python
  messaging.Message(
      token=token,
      notification=messaging.Notification(title=..., body=...),
      data={"soc", "threshold", "direction", "fired_at"},
      android=messaging.AndroidConfig(
          priority="high",
          notification=messaging.AndroidNotification(
              channel_id="hermes_alerts",
              icon="@drawable/ic_notification",
          ),
      ),
  )
  ```
- Logs every send result (`[fcm] sent ok ...` / `[fcm] send failed (code=...)`).
- **Prunes stale tokens**: tokens failing with `NOT_FOUND`/`INVALID_ARGUMENT`/
  `UNREGISTERED` are removed from `devices.json` (self-cleaning after app
  reinstalls / token rotation).
- The `notification` payload means Google Play services displays the alert even
  when the app is backgrounded/killed; `priority="high"` delivers during Doze.
  `onMessageReceived` in the app re-renders it (same channel/icon) when foregrounded.

### Data sources (non-trigger routes)

- **Modbus**: hand-rolled TCP socket to slave 247; input registers for grid/PV/
  battery power and SOC (see `modbus_read_ir`, `to_i32`).
- **Home Assistant**: REST `call_ha`/`get_ha_state`; sensors for SOC, health,
  capacity, daily energy, plant status, forecast.
- **Miner**: bosminer GraphQL for hashrate/power/autotuning status and power
  preset mutations.

### Testing

```bash
python3 -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest tests -q      # 15 tests: trigger eval + action validation
```
`tests/test_trigger.py` covers `trigger_condition_met`, `validate_actions`,
`should_toggle_miner`, `should_set_preset`, and SOC scaling (`read_soc`).

---

## Android app (`SigenergyBattery`)

Kotlin 2.2 / Compose (Material 3) / AGP 9.2.1. `minSdk 31`, `targetSdk 36`,
`compileSdk 37`. Package `com.github.diarmaidlindsay.sigenergybattery`.

### Architecture

Simple single-activity MVVM with manual DI (`AppContainer`). No navigation
library, no Hilt — a `ViewModel` + Compose state is all it needs.

```
app/src/main/java/com/github/diarmaidlindsay/sigenergybattery/
├── SigenergyBatteryApp.kt        Application; creates AppContainer + notification channels
├── MainActivity.kt               Compose host; requests POST_NOTIFICATIONS
├── core/di/AppContainer.kt       manual DI: settingsStore, createApi()
├── core/notifications/NotificationHelper.kt   alert channel + alertFromPush()
├── data/api/HermesApi.kt         Retrofit interface + DTOs + ApiClientFactory
├── data/local/SettingsStore.kt   DataStore persistence + interface (fakes in tests)
├── domain/BatteryMonitor.kt      pure trigger-action validation (mirrored on bridge)
├── domain/SocEtaCalculator.kt    ETA formatting for the status line
├── domain/model/BatteryModels.kt BridgeConfig, MonitorConfig, enums
├── push/FcmService.kt            FCM receiver + token registration
├── service/PollingState.kt       singleton shared state (last SOC, active, alertFired)
└── ui/MonitorScreen.kt           Compose UI
    ui/MonitorViewModel.kt        state + bridge API orchestration
```

**Key design point — the app is stateless about scheduling.** The foreground
service (`PollingService`) was removed. `PollingState` remains as a lightweight
singleton that the `ViewModel` syncs from `GET /api/trigger` and FCM pushes.

### Networking (`HermesApi` / `ApiClientFactory`)

- Retrofit + `kotlinx.serialization` converter, Bearer auth interceptor using the
  user-entered API key, 10 s connect / 20 s read timeouts.
- DTOs: `SolarNowDto`, `SolarHistoryDto`, `MinerStatusDto`, `TriggerConfigDto`,
  `TriggerStatusDto`, `DeviceRegisterDto`, ack DTOs.
- All endpoints map 1:1 to the bridge table above.

### ViewModel flows (`MonitorViewModel`)

- **Connect** → `GET /api/solar/now` with a 30 s timeout; on success saves config,
  marks connected, loads history, and `syncTriggerStatus()`.
- **Start monitoring** (`beginMonitoring`) → saves `MonitorConfig` locally, then
  `POST /api/trigger` with `TriggerConfigDto` (`actions` mapped from
  `TriggerAction` enums, `miner_preset` sent as slug `low`/`efficient`/`max`),
  sets `monitoring=true`, and **registers the FCM token** (`POST /api/device`).
- **Stop monitoring** (`cancelMonitoring`) → `DELETE /api/trigger`, clears state.
- **Disconnect** → `DELETE /api/trigger` only if the
  `cancelTriggerOnDisconnect` setting is on (see Settings below); always drops the
  local connection.
- **Sync** (`syncTriggerStatus`) → `GET /api/trigger`; reconciles `monitoring`
  (`enabled && !fired`), `alertFired`, `lastSoc`, and `lastChecked`
  (bridge seconds × 1000 → millis). Runs on connect and every `refreshAll()`.
- **Refresh** → `checkNow()` + `loadHistory()` + `syncTriggerStatus()` on every
  screen resume.

### Settings (`SettingsStore`, DataStore-backed)

- `bridgeConfig` (host/port/apiKey), `monitorConfig` (interval/threshold/direction/
  actions/preset), `hasConnectedBefore`, and
  `cancelTriggerOnDisconnect` (default **true**).
- The setting **Stop bridge monitoring on disconnect** (toggled via the settings
  gear in the alert card) controls whether Disconnect cancels the bridge trigger.
  Off means the bridge keeps monitoring even after the app disconnects.

### Push (`FcmService`)

- `onMessageReceived` → builds the alert via `NotificationHelper.alertFromPush`
  (soc/threshold/direction from `data`), posts to `NOTIF_ID_ALERT`, and sets
  `PollingState.alertFired` so an open app reflects the fire instantly.
- `onNewToken` → re-registers the token with the saved bridge config (best-effort).
- **Important:** the `hermes_alerts` channel is created in
  `SigenergyBatteryApp.onCreate()`. If the channel does not exist, Android silently
  drops FCM notifications. POST_NOTIFICATIONS (Android 13+) is also required and is
  **reset on app reinstall** — the app requests it via the Start-monitoring button.

### UI (`MonitorScreen`)

- Connect panel (host/port/API key) when not connected; otherwise the monitor panel:
  SOC card, 24 h history chart (Vico), Check-now/Disconnect, and the alert-settings
  card (interval dropdown, threshold slider, direction chips, trigger-action
  checkboxes, miner preset chips, settings gear for the disconnect toggle, and the
  Start/Stop-monitoring control that reflects bridge state).
- **Strategy card** (`ui/components/StrategySection.kt`) below the alert card:
  seasonal template picker (Summer/Spring/Autumn from the bridge), an editable
  step list (SOC slider, direction chips, optional earliest-time field, action
  checkboxes, preset chips), active-hours + interval fields, and Start/Stop.
  While running it highlights the active step and shows the last transition and
  SOC. Mutual-exclusivity confirmations show when starting a strategy would
  cancel a trigger (or vice versa).

### Testing

```bash
./gradlew :app:testDebugUnitTest
```
- `MonitorViewModelTest` (32 tests): connect, history, auto-connect, trigger-action
  normalization, arming posts the right `TriggerConfigDto`, failure paths, disconnect
  respects the setting, and fired-state sync from the bridge.
- `BatteryMonitorTest`, `SocEtaCalculatorTest`, DTO parsing tests.
- Fakes in `fakes/Fakes.kt`: `FakeSettingsStore`, `FakeHermesApi`, `HangingHermesApi`.
  `PollingState` is reset in `@Before` to avoid cross-test leakage.

---

## End-to-end scenarios

**Arming (app → bridge):**
1. User sets interval/threshold/actions/preset in the app and taps Start.
2. App `POST /api/trigger`; the bridge validates, persists to `trigger.json`, and
   wakes the worker.
3. Worker checks immediately (then every `interval_minutes`): reads SOC via Modbus,
   compares to the threshold.

**Trigger fires (bridge, no phone involvement):**
1. SOC crosses the threshold.
2. Worker marks `fired=true` (persisted), runs miner actions idempotently, sends FCM
   to every registered token.
3. Google Play services shows the alert on the phone (foreground: the app renders it;
   background/killed/Doze: the system displays the `notification` payload).
4. Bridge stops checking until re-armed.

**App opened later:**
1. Auto-connects to the saved bridge config.
2. `syncTriggerStatus()` reflects `fired=true` → UI shows "Alert fired. Monitoring
   stopped automatically." with a Monitor-again button.

---

## Pitfalls & lessons (read before changing things)

1. **FCM sender project must match the app's Firebase project.** The bridge's
   service-account key and the app's `google-services.json` must come from the same
   project (`sigminer-ed92f`). A key from a differently-named project fails with
   `PermissionDeniedError` (FCM API disabled) or sender-mismatch.
2. **`firebase-admin` uses `icon`, not `small_icon`**, on `AndroidNotification`.
   `small_icon` raises a `TypeError` that is caught and swallowed per-token — the
   symptom is "miner action worked, push never arrived" with only a log line to
   diagnose. Grep `[fcm]` in `docker logs hermes-bridge`.
3. **Notification channel creation is mandatory.** FCM notifications are silently
   dropped if the target channel doesn't exist. Create channels in
   `Application.onCreate` (not in a service that may never start).
4. **Reinstall resets POST_NOTIFICATIONS.** After uninstalling, permission is denied
   and pushes won't display until re-granted.
5. **Trigger is one-shot.** It never re-fires until re-armed via `POST /api/trigger`.
6. **`Disconnect` cancels the trigger by default** (setting `cancelTriggerOnDisconnect`),
   so leaving the app without a stop tap stops monitoring unless the user toggles it.
7. **Stale FCM tokens self-prune** on send failure — no manual cleanup of
   `devices.json` needed, but tokens only get pruned when a send actually happens.
8. **Never arm a test trigger containing `MINER_ON`/`MINER_OFF`/`SET_POWER_PRESET`**
   on a live system — it will act on real hardware. Test FCM with a NOTIFY-only
   trigger or by calling `send_fcm_alert()` directly.

---

## Contributing / getting started

1. Clone the bridge (`ssh://git@docker.lan:222/diarmaid/hermes-bridge.git`) and the
   app (`~/Coding/Android/SigenergyBattery`).
2. Bridge: edit `main.py`, add/adjust `tests/test_trigger.py`, run pytest locally,
   then `scp` + `docker compose up -d --build` on docker.
3. App: edit Kotlin under `app/src/main/java`, update fakes/tests, run
   `./gradlew :app:testDebugUnitTest`, build and `adb install` to a device.
4. Keep the bridge and app trigger logic in sync: `BatteryMonitor.kt`
   (`normalizeActions`) ↔ `main.validate_actions`, and the miner idempotency rules.
