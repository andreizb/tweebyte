package ro.tweebyte.equivalence.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import ro.tweebyte.equivalence.support.DbReset;
import ro.tweebyte.equivalence.support.ScenarioContext;

/**
 * Per-scenario lifecycle: clear DBs + scenario state before each scenario;
 * dump body of failed scenarios to the report on failure.
 */
public class Hooks {

    @Before(order = 0)
    public void clearStateAndDb() {
        ScenarioContext.reset();
        DbReset.clearAll();
    }

    @After
    public void onFailureDumpResponse(Scenario scenario) {
        if (!scenario.isFailed()) return;
        ScenarioContext ctx = ScenarioContext.current();
        scenario.attach(
                "Last HTTP status: " + ctx.lastStatus + "\nLast body:\n" + ctx.lastBody,
                "text/plain",
                "last-http-response");
    }
}
