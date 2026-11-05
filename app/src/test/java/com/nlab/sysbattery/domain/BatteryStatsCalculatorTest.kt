package com.nlab.sysbattery.domain

import com.nlab.sysbattery.data.BatterySysfsReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryStatsCalculatorTest {
    private fun sample(time: Long, level: Int, chargeCounter: Long? = null) =
        BatterySysfsReader.Snapshot(
            timestampMillis = time,
            levelPercent = level,
            currentMicroAmps = -500_000L,
            voltageMilliVolts = 4_000L,
            chargeCounterMicroAmpHours = chargeCounter,
            temperatureTenthsCelsius = 340,
            source = "test"
        )

    @Test
    fun currentDerivedRateIsUnavailableWithoutRealOrConfiguredCapacity() {
        val current = sample(0L, 80, 4_000_000L).copy(
            currentMicroAmps = -274_000L,
            designCapacityMicroAmpHours = null,
            fullChargeCapacityMicroAmpHours = null,
            estimatedFullChargeCapacityMicroAmpHours = null
        )
        val result = BatteryStatsCalculator.calculateWindowed(
            samples = listOf(BatteryStatsCalculator.TimedSample(0L, 80, -274_000L, 4_000_000L)),
            current = current,
            isCharging = false
        )
        assertNull(result.batteryPercentPerHour)
        assertEquals(274.0, result.currentMilliAmpsMagnitude!!, 0.01)
    }

    @Test
    fun chargingPercentageIncreaseIsNotReportedAsDrain() {
        val result = BatteryStatsCalculator.calculate(
            previous = BatteryStatsCalculator.PreviousSample(0L, 40, -500_000L, 2_000_000L),
            current = sample(600_000L, 45, 2_200_000L),
            isCharging = true
        )
        assertNull(result.batteryPercentPerHour)
        assertEquals(BatteryStatsCalculator.Direction.CHARGING, result.direction)
    }

    @Test
    fun unavailableCurrentDoesNotPreventPercentageRateCalculation() {
        val noCurrent = sample(1_200_000L, 39, 2_000_000L).copy(currentMicroAmps = null)
        val result = BatteryStatsCalculator.calculate(
            previous = BatteryStatsCalculator.PreviousSample(600_000L, 40, null, 2_100_000L),
            current = noCurrent,
            isCharging = false
        )
        assertEquals(6.0, result.batteryPercentPerHour!!, 0.001)
        assertNull(result.currentMilliAmpsMagnitude)
    }

    @Test
    fun equalBatteryLevelDoesNotInventPercentageDrain() {
        val result = BatteryStatsCalculator.calculate(
            previous = BatteryStatsCalculator.PreviousSample(0L, 45, null, null),
            current = sample(600_000L, 45),
            isCharging = false
        )
        assertNull(result.batteryPercentPerHour)
        assertEquals(BatteryStatsCalculator.Direction.DISCHARGING, result.direction)
    }

    @Test
    fun windowedRateWaitsForEnoughSamples() {
        val current = sample(60_000L, 49, 1_900_000L)
        val samples = listOf(
            BatteryStatsCalculator.TimedSample(0L, 50, -500_000L, 2_000_000L),
            BatteryStatsCalculator.TimedSample(20_000L, 50, -480_000L, 1_990_000L),
            BatteryStatsCalculator.TimedSample(40_000L, 50, -510_000L, 1_950_000L),
            BatteryStatsCalculator.TimedSample(60_000L, 49, -500_000L, 1_900_000L)
        )
        val result = BatteryStatsCalculator.calculateWindowed(samples, current, false)
        assertNull(result.batteryPercentPerHour)
        assertEquals(BatteryStatsCalculator.Direction.DISCHARGING, result.direction)
        org.junit.Assert.assertNotNull(result.currentMilliAmpsMagnitude)
        org.junit.Assert.assertNotNull(result.powerWattsMagnitude)
    }

    @Test
    fun rollingCurrentAndCapacityProducePercentRateBeforeLevelChanges() {
        val current = sample(60_000L, 50, 2_000_000L).copy(
            fullChargeCapacityMicroAmpHours = 5_000_000L
        )
        val samples = listOf(
            BatteryStatsCalculator.TimedSample(0L, 50, -500_000L, 2_500_000L),
            BatteryStatsCalculator.TimedSample(20_000L, 50, -510_000L, 2_490_000L),
            BatteryStatsCalculator.TimedSample(40_000L, 50, -490_000L, 2_480_000L),
            BatteryStatsCalculator.TimedSample(60_000L, 50, -500_000L, 2_470_000L)
        )
        val result = BatteryStatsCalculator.calculateWindowed(samples, current, false)
        assertEquals(10.0, result.batteryPercentPerHour!!, 0.1)
    }

    @Test
    fun windowedRateCalculatesRateFromThreeMinuteLevelWindow() {
        val current = sample(180_000L, 49, 1_900_000L)
        val samples = listOf(
            BatteryStatsCalculator.TimedSample(0L, 50, -500_000L, 2_000_000L),
            BatteryStatsCalculator.TimedSample(60_000L, 50, -480_000L, 1_990_000L),
            BatteryStatsCalculator.TimedSample(120_000L, 50, -510_000L, 1_950_000L),
            BatteryStatsCalculator.TimedSample(180_000L, 49, -500_000L, 1_900_000L)
        )
        val result = BatteryStatsCalculator.calculateWindowed(samples, current, false)
        assertEquals(20.0, result.batteryPercentPerHour!!, 0.001)
    }

    @Test
    fun dischargeRateUsesOnlyPositivePercentageDrop() {
        val result = BatteryStatsCalculator.calculate(
            previous = BatteryStatsCalculator.PreviousSample(0L, 45, -500_000L, 2_200_000L),
            current = sample(600_000L, 44, 2_100_000L),
            isCharging = false
        )
        assertEquals(6.0, result.batteryPercentPerHour!!, 0.001)
        assertEquals(BatteryStatsCalculator.Direction.DISCHARGING, result.direction)
    }

    @Test
    fun notificationAndDurationFormattingMatchesSpec() {
        assertEquals("5h 57m 26s", com.nlab.sysbattery.service.BatteryMonitorService.formatDuration(((5 * 3600) + (57 * 60) + 26) * 1_000L))
        assertEquals("1m 9s", com.nlab.sysbattery.service.BatteryMonitorService.formatDuration(69_000L))
        assertEquals("56s", com.nlab.sysbattery.service.BatteryMonitorService.formatDuration(56_000L))
        assertEquals("13s", com.nlab.sysbattery.service.BatteryMonitorService.formatDuration(13_000L))
        assertEquals("5h 57m 26s (28%)", com.nlab.sysbattery.service.BatteryMonitorService.formatTimeDrop(((5 * 3600) + (57 * 60) + 26) * 1_000L, 28.0))
        assertEquals("1m 9s (0%)", com.nlab.sysbattery.service.BatteryMonitorService.formatTimeDrop(69_000L, 0.0))
        assertEquals("3m 42s (0.3%)", com.nlab.sysbattery.service.BatteryMonitorService.formatTimeDrop(222_000L, 0.34))
        assertEquals("4.70% /hr", com.nlab.sysbattery.service.BatteryMonitorService.formatNotificationRate(4.7))
        assertEquals("0% /hr", com.nlab.sysbattery.service.BatteryMonitorService.formatNotificationRate(0.0))

        val service = com.nlab.sysbattery.service.BatteryMonitorService()
        val tele = com.nlab.sysbattery.service.BatteryMonitorService.SessionTelemetry(
            activeDrainRateValue = 4.70,
            idleDrainRateValue = 0.80,
            activeLossPercent = 2.0,
            idleLossPercent = 0.5,
            deepSleepLossPercent = 0.2,
            awakeLossPercent = 0.3,
            screenOnMillis = 222_000L,
            screenOffMillis = 26_000L,
            deepSleepMillis = 18_000L,
            awakeMillis = 8_000L,
            deepSleepShare = 69.2,
            awakeShare = 30.8
        )
        val payload = service.buildNotificationPayload(
            sample = sample(0L, 80),
            rates = null,
            charging = false,
            tele = tele,
            showBatteryPercentage = true,
            showChargingPower = true,
            temperatureUnit = "Celsius (°C)",
            idleWarningText = null
        )
        assertEquals("Screen on: 3m 42s (2%)", payload.screenOnLine)
        assertEquals("Screen off: 26s (0.5%)", payload.screenOffLine)
        assertEquals("Deep sleep: 18s (0.2%)", payload.deepSleepLine)
        assertEquals("Awake: 8s (0.3%)", payload.awakeLine)
    }

    @Test
    fun singleSampleWithCapacityProducesImmediateRealTimeDischargeRate() {
        val current = sample(0L, 80, 4_000_000L).copy(
            currentMicroAmps = -274_000L,
            designCapacityMicroAmpHours = 5_000_000L
        )
        val result = BatteryStatsCalculator.calculateWindowed(
            samples = listOf(BatteryStatsCalculator.TimedSample(0L, 80, -274_000L, 4_000_000L)),
            current = current,
            isCharging = false
        )
        assertEquals(274.0, result.currentMilliAmpsMagnitude!!, 0.01)
        assertEquals(5.48, result.batteryPercentPerHour!!, 0.01)
    }

    @Test
    fun differentialFormulaMatchesSpecificationExamples() {
        // #101 ACTIVE: 95% -> 92% over 18 minutes = 10.0%/h
        val s101 = com.nlab.sysbattery.data.BatterySessionDbHelper.calculateSessionRatePerHour(
            startBatteryPercent = 95.0,
            endBatteryPercent = 92.0,
            elapsedMillis = 18L * 60_000L
        )
        assertEquals(10.0, s101!!, 0.01)

        // #102 IDLE: 92% -> 91% over 2 hours 30 minutes (150m) = 0.4%/h
        val s102 = com.nlab.sysbattery.data.BatterySessionDbHelper.calculateSessionRatePerHour(
            startBatteryPercent = 92.0,
            endBatteryPercent = 91.0,
            elapsedMillis = 150L * 60_000L
        )
        assertEquals(0.4, s102!!, 0.01)

        // #103 ACTIVE: 91% -> 86% over 45 minutes = 6.67%/h
        val s103 = com.nlab.sysbattery.data.BatterySessionDbHelper.calculateSessionRatePerHour(
            startBatteryPercent = 91.0,
            endBatteryPercent = 86.0,
            elapsedMillis = 45L * 60_000L
        )
        assertEquals(6.666, s103!!, 0.01)

        // Guard rails: near-zero duration or negative delta (charging) return null
        assertNull(
            com.nlab.sysbattery.data.BatterySessionDbHelper.calculateSessionRatePerHour(
                startBatteryPercent = 90.0,
                endBatteryPercent = 89.0,
                elapsedMillis = 1_000L
            )
        )
        assertNull(
            com.nlab.sysbattery.data.BatterySessionDbHelper.calculateSessionRatePerHour(
                startBatteryPercent = 85.0,
                endBatteryPercent = 88.0,
                elapsedMillis = 30L * 60_000L
            )
        )
    }

    @Test
    fun milliAmpFuelGaugeAboveOneAmpNormalizesCorrectlyToMicroAmps() {
        // Realme RMX3461 reports current_now in mA (e.g., -274, -872, -1250)
        assertEquals(-274_000L, BatterySysfsReader.normalizeRawCurrentToMicroAmps(-274L))
        assertEquals(-872_000L, BatterySysfsReader.normalizeRawCurrentToMicroAmps(-872L))
        assertEquals(-1_250_000L, BatterySysfsReader.normalizeRawCurrentToMicroAmps(-1250L))
        assertEquals(-850_000L, BatterySysfsReader.normalizeRawCurrentToMicroAmps(-850_000L))
        assertEquals("0.03%", com.nlab.sysbattery.service.BatteryMonitorService.formatDropPercent(0.031))
        assertEquals("0.01%", com.nlab.sysbattery.service.BatteryMonitorService.formatDropPercent(0.012))
    }
}
