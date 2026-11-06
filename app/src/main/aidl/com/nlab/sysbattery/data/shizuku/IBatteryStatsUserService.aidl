package com.nlab.sysbattery.data.shizuku;

interface IBatteryStatsUserService {
    String readChargedBatteryStats();
    String readBatteryStatsHistory();
    String readAlarmStats();
    String readJobSchedulerStats();
}
