# SysBattery reverse-engineering specification

## Scope and evidence limits

Target reference: Franco Kernel Manager v7.0.48 (Premium) and user-provided Battery Monitor screenshots. The APK archive and DEX string table were inspected, but method-body decompilation was not completed in the available environment. Therefore, string references are not proof that a specific code path is used by Battery Monitor.

- **[SEEN]** DEX references include `BatteryMonitorActivity`, `BatteryMonitor`, `BatteryMonitorOptions`, `AppBatteryMonitor`, and `NewBatteryMonitorService`.
- **[SEEN]** Screenshots show app usage mAh values, screen-on/off durations, active/idle drain per hour, component estimated power use, charge/discharge totals, wake-lock statistics, and configuration toggles.
- **[INFERRED]** `NewBatteryMonitorService` likely coordinates background sampling or statistic updates; its exact responsibilities are unverified.
- **[UNKNOWN]** Exact polling interval, original battery-health/degradation formula, and component attribution algorithm.
- **[UNKNOWN]** Whether every listed sysfs node belongs to Battery Monitor or another feature in the APK.

## Candidate sysfs nodes

| Path | Evidence | Notes |
|---|---|---|
| `/sys/class/power_supply/battery/current_now` | [INFERRED] standard Linux Android candidate | Commonly microamps; sign convention varies. |
| `/sys/class/power_supply/battery/voltage_now` | [INFERRED] standard Linux Android candidate | Commonly microvolts; sanity-check before conversion. |
| `/sys/class/power_supply/battery/capacity` | [INFERRED] standard Linux Android candidate | Integer percentage on many devices. |
| `/sys/class/power_supply/battery/charge_counter` | [INFERRED] standard Linux Android candidate | Commonly microamp-hours; vendor-specific availability. |
| `/sys/class/power_supply/bms/current_now` | [INFERRED] vendor-specific candidate | May be absent or inaccessible. |
| `/sys/class/power_supply/bms/charge_counter` | [INFERRED] vendor-specific candidate | May be absent or inaccessible. |
| `/sys/class/power_supply/usb/voltage_now` | [SEEN] path string in DEX | Not proven to be exclusively used by Battery Monitor. |

## Recreation formulas (not claimed as original formulas)

1. Current conversion: `mA = µA / 1000`.
2. Voltage conversion for a plausible microvolt node: `mV = µV / 1000`.
3. Percentage rate: `abs(currentPercent - previousPercent) × 3,600,000 / elapsedMilliseconds`.
4. Current-integrated capacity estimate: `abs(current_mA) × elapsedMilliseconds / 3,600,000 = mAh`.
5. Charge-counter delta: `(current_µAh - previous_µAh) / 1000 = mAh`.

The direction of current is vendor/kernel-dependent. The app should prefer Android battery charging status when available. Short sampling windows can make percentage-per-hour estimates noisy because percentage is quantized.

## Polling and service behavior

The service defaults to a 1-second sampling interval (bounded to 1–60 seconds through its start API). There is no user-facing interval control. **This is an implementation choice, not a recovered original interval.** The service starts from the app while monitoring is enabled, and boot recovery restarts it only when the persisted monitor preference is enabled. Live state is published in-process via `StateFlow`.

## Known functional gaps

This implementation does not persist raw timestamped battery samples to SQLite. The chart uses in-memory samples for the current app process. Completed ACTIVE/IDLE session summaries are stored locally in SQLite with a seven-day retention window and a maximum of 250 records. Per-app power attribution, reliable historical deep-sleep accounting, wake-lock analysis, and component power estimates remain unimplemented and require verified data sources and device testing.


## Session history stored by SysBattery

The app stores completed ACTIVE/IDLE discharge-session summaries in SQLite: session type, elapsed start/end timestamps, start/end battery levels, measured drop percentage, calculated rate, and the wall-clock time the session was recorded. Records are pruned after seven days and capped at 250. Raw per-sample telemetry is not stored in SQLite; the dashboard chart keeps a bounded in-memory sample list for the current app process. The session schema is an implementation choice, not a reproduction of another app's private database or calculation algorithm.
