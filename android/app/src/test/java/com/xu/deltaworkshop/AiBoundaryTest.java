package com.xu.deltaworkshop;
import org.junit.Test;
import static org.junit.Assert.*;
public class AiBoundaryTest {
    @Test public void chatCannotAuthorizePurchases() throws Exception {
        for(String action:new String[]{"approved","buy","run_script","tap"}) {
            try{AiClient.allowedAction("workshop_action","{\"action\":\""+action+"\"}");fail("Unexpected purchase or arbitrary execution capability");}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void chatCannotInjectBudgetOrPlan() throws Exception {
        try{AiClient.allowedAction("workshop_action","{\"action\":\"cycle\",\"approvedPlan\":\"test-plan\",\"purchaseBudget\":999999}");fail("AI must not supply approval metadata");}catch(IllegalArgumentException expected){}
    }
    @Test public void fourNonPurchasingEntrypointsAreAccepted() throws Exception {
        for(String action:AiClient.ACTIONS)assertEquals(action,AiClient.allowedAction("workshop_action","{\"action\":\""+action+"\"}"));
    }
}
