/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Single JUnit 5 Platform Suite that discovers every .feature under
 * src/test/resources/features and runs it through Cucumber.
 *
 * Stack-under-test (async vs reactive) is selected by the Maven profile (-Pasync /
 * -Preactive); see CompositeLifecycle for the system-property hookup.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME,
		value = "ro.tweebyte.equivalence.steps,ro.tweebyte.equivalence.hooks")
@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME,
		value = "pretty,summary,html:target/cucumber-report.html,json:target/cucumber-report.json")
public class RunCucumberTests {

}
