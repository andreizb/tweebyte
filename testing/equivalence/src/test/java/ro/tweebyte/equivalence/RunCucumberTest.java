package ro.tweebyte.equivalence;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Single JUnit 5 Platform Suite that discovers every .feature under
 * src/test/resources/features and runs it through Cucumber.
 *
 * Stack-under-test (async vs reactive) is selected by the Maven profile
 * (-Pasync / -Preactive); see CompositeLifecycle for the system-property hookup.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "ro.tweebyte.equivalence.steps,ro.tweebyte.equivalence.hooks")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,summary,html:target/cucumber-report.html,json:target/cucumber-report.json")
public class RunCucumberTest {
}
