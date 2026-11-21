package com.xu.deltaworkshop;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunServiceTest {
    @Test public void onlyCompletedCycleClosesGameAndLocksScreen() throws Exception {
        JSONObject completed = new JSONObject("{\"ok\":true}");
        assertTrue(RunService.shouldCloseAndLock("cycle", completed));
        assertFalse(RunService.shouldCloseAndLock("check", completed));
        assertFalse(RunService.shouldCloseAndLock("harvest", completed));
        assertFalse(RunService.shouldCloseAndLock("quote", completed));
        assertFalse(RunService.shouldCloseAndLock("cycle", new JSONObject("{\"ok\":false}")));
        assertFalse(RunService.shouldCloseAndLock("cycle", new JSONObject("{\"ok\":true,\"unresolvedPurchase\":true}")));
        assertFalse(RunService.shouldCloseAndLock("cycle", new JSONObject("{\"ok\":true,\"needsApproval\":true}")));
        assertFalse(RunService.shouldCloseAndLock("cycle", null));
    }
}
