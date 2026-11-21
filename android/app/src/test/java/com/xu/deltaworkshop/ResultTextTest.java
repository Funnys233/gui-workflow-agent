package com.xu.deltaworkshop;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class ResultTextTest {
    @Test public void partialFillIsNotReportedAsExactSpending() throws Exception {
        JSONObject result=new JSONObject("{\"phase\":\"finished\",\"spendingExact\":false,\"confirmedSpent\":41591,\"spentUpperBound\":255003}");
        String summary=ResultText.summary(result);
        assertTrue(summary.contains("不超过 255,003"));assertFalse(summary.contains("花费 41,591"));
    }
    @Test public void unverifiedBusyIsNotClaimedAsConfirmedManufacturing() throws Exception {
        JSONObject result=new JSONObject("{\"finalStates\":[{\"name\":\"技术中心\",\"state\":\"busy_or_unknown\"}]}");
        assertEquals("制造中或尚未确认",ResultText.station(result,"技术中心"));
    }
    @Test public void pendingPartialPurchaseRequiresReconciliation() throws Exception {
        JSONObject result=new JSONObject("{\"phase\":\"failed\",\"unresolvedPurchase\":true}");
        assertTrue(ResultText.summary(result).contains("勿重复补料"));
    }
    @Test public void collectedItemIsShownAsIdleAfterPartialFailure() throws Exception {
        JSONObject result=new JSONObject("{\"phase\":\"failed\",\"stations\":[{\"name\":\"工作台\",\"before\":\"ready\",\"after\":\"idle\"}],\"collected\":[\"工作台\"]}");
        assertEquals("空闲",ResultText.station(result,"工作台"));
        assertTrue(ResultText.summary(result).contains("已收获 1 件"));
    }
}
