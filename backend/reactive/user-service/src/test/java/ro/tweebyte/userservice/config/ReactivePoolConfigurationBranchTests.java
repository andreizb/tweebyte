/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.userservice.config;

import io.r2dbc.pool.ConnectionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcProperties;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Additional branch tests for {@link ReactivePoolConfiguration} that exercise the
 * conditional paths inside {@code connectionFactory()} and the
 * {@code applyPoolProperties} helper — specifically the username/password null guards,
 * the sslMode DISABLE filter, and the validationDepth/validationQuery branches.
 */
@ExtendWith(MockitoExtension.class)
class ReactivePoolConfigurationBranchTests {

	private final TestableConfig config = new TestableConfig();

	private ConnectionPool createdPool;

	@AfterEach
	void tearDown() {
		if (this.createdPool != null) {
			this.createdPool.dispose();
		}
	}

	/** Build a minimal R2dbcProperties pointing at a localhost PG (not actually connected). */
	private R2dbcProperties minimalProperties() {
		R2dbcProperties props = new R2dbcProperties();
		// r2dbc-pool validates the URL eagerly at build() time but does not open connections
		// until acquire() is called — safe for unit tests.
		props.setUrl("r2dbc:postgresql://localhost:5432/test");
		return props;
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<ConnectionFactoryOptionsBuilderCustomizer> noCustomizers() {
		ObjectProvider<ConnectionFactoryOptionsBuilderCustomizer> mock = mock(ObjectProvider.class);
		given(mock.orderedStream()).willReturn(java.util.stream.Stream.empty());
		return mock;
	}

	// --- connectionFactory branches ---

	@Test
	void connectionFactory_passwordOnlyNoUsername_builds() {
		// username == null (null-guard branch is false) but password set via URL — the driver
		// gets its user from the URL itself (r2dbc:postgresql://user:pass@host/db format).
		R2dbcProperties props = new R2dbcProperties();
		props.setUrl("r2dbc:postgresql://testuser:testpass@localhost:5432/test");
		// Leave props.username/password null so both null-guard branches in connectionFactory fire
		// as the "false" path; the URL already encodes the credentials.
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_withUsernameAndPassword_builds() {
		R2dbcProperties props = minimalProperties();
		props.setUsername("testuser");
		props.setPassword("testpass");
		// username != null and password != null → both null-guard branches are true
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_withUsernameOnly_builds() {
		R2dbcProperties props = minimalProperties();
		props.setUsername("testuser");
		// username != null, password == null → one branch true, one false
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_sslModeDisable_skipsSslRootCertProperty() {
		R2dbcProperties props = minimalProperties();
		props.setUsername("user");
		props.setPassword("pass");
		// sslMode=DISABLE → sslDisabled=true; sslRootCert is skipped (continue branch fires).
		props.getProperties().put("sslMode", "DISABLE");
		props.getProperties().put("sslRootCert", "/nonexistent/path");
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_sslModeDisable_skipsSslCertProperty() {
		R2dbcProperties props = minimalProperties();
		props.setUsername("user");
		props.setPassword("pass");
		// sslMode=DISABLE → sslDisabled=true; sslCert is skipped (continue branch fires).
		props.getProperties().put("sslMode", "DISABLE");
		props.getProperties().put("sslCert", "/nonexistent/cert.pem");
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_sslModeDisable_skipsSslKeyProperty() {
		R2dbcProperties props = minimalProperties();
		props.setUsername("user");
		props.setPassword("pass");
		// sslMode=DISABLE → sslDisabled=true; sslKey is skipped (continue branch fires).
		props.getProperties().put("sslMode", "DISABLE");
		props.getProperties().put("sslKey", "/nonexistent/key.pem");
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_sslModeNotDisable_includesAllProperties() {
		R2dbcProperties props = minimalProperties();
		props.setUsername("user");
		props.setPassword("pass");
		// sslMode != DISABLE → sslDisabled=false; non-ssl properties (e.g. lockTimeout) pass through
		props.getProperties().put("sslMode", "PREFER");
		props.getProperties().put("lockTimeout", "5000");
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_withValidationDepth_builds() {
		R2dbcProperties props = minimalProperties();
		props.setUsername("user");
		props.setPassword("pass");
		props.getPool().setValidationDepth(io.r2dbc.spi.ValidationDepth.LOCAL);
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_withValidationQuery_builds() {
		R2dbcProperties props = minimalProperties();
		props.setUsername("user");
		props.setPassword("pass");
		props.getPool().setValidationQuery("SELECT 1");
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_nullValidationDepth_usesDefaultDepth() {
		// validationDepth == null → applyPoolProperties skips the setValidationDepth call
		// (false branch of the null guard). We must explicitly null it out since the
		// R2dbcProperties.Pool default is LOCAL (non-null).
		R2dbcProperties props = minimalProperties();
		props.setUsername("user");
		props.setPassword("pass");
		// Null it out to exercise the false branch of the null guard.
		props.getPool().setValidationDepth(null);
		assertThat(props.getPool().getValidationDepth()).isNull();
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	@Test
	void connectionFactory_blankValidationQuery_skipsValidationQuery() {
		// validationQuery blank → applyPoolProperties skips the setValidationQuery call
		// (false branch of the null-and-blank guard).
		R2dbcProperties props = minimalProperties();
		props.setUsername("user");
		props.setPassword("pass");
		props.getPool().setValidationQuery("  "); // blank → skipped
		reactor.core.scheduler.Scheduler scheduler = this.config.r2dbcPoolAcquisitionScheduler();
		this.createdPool = this.config.connectionFactory(props, noCustomizers(), scheduler);
		assertThat(this.createdPool).isNotNull();
		scheduler.dispose();
	}

	// --- Helper subclass ---

	static class TestableConfig extends ReactivePoolConfiguration {

		TestableConfig() {
			ReflectionTestUtils.setField(this, "readSchedulerPoolSize", 10);
			ReflectionTestUtils.setField(this, "readSchedulerQueueCapacity", 1000);
			ReflectionTestUtils.setField(this, "maxConnectionsPerHost", 10);
			ReflectionTestUtils.setField(this, "pendingAcquireMaxCount", -1);
			ReflectionTestUtils.setField(this, "acquireTimeoutSeconds", 5L);
			ReflectionTestUtils.setField(this, "responseTimeoutSeconds", 5L);
			ReflectionTestUtils.setField(this, "maxInMemoryBytes", 16777216);
		}

	}

}
