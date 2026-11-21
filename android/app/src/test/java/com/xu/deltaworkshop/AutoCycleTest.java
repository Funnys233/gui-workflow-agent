package com.xu.deltaworkshop;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class AutoCycleTest {
    @Test public void usesLatestTimerAndRoundsUpAfterBuffer() throws Exception {
        JSONObject result = new JSONObject("""
            {"manufacturing":[{"finalRemaining":"07:18:58"},{"finalRemaining":"04:59:59"},
                {"status":"over_station_cap"},{"finalRemaining":"07:00:00"}]}
            """);
        assertEquals(440 * 60_000L, AutoCycle.delayMillis(result));
    }

    @Test public void wholeMinuteGetsExactlyOneExtraMinute() throws Exception {
        JSONObject result = new JSONObject("""
            {"manufacturing":[{"finalRemaining":"07:00:00"}]}
            """);
        assertEquals(421 * 60_000L, AutoCycle.delayMillis(result));
    }

    @Test public void noConfirmedTimerDoesNotArmNextRound() throws Exception {
        JSONObject result = new JSONObject("""
            {"manufacturing":[{"remaining":"07:00:00"},{"finalRemaining":"07:99:00"}]}
            """);
        assertEquals(0, AutoCycle.delayMillis(result));
    }
}
