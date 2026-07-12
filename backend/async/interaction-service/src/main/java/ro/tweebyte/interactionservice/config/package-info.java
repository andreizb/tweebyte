/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

/**
 * Spring configuration for the interaction service: the Redis cache wiring, the JPA
 * datasource and thread-pool tuning, and the Spring MVC/RestClient setup that backs the
 * blocking transport.
 *
 * @author Andrei Zbarcea
 */
package ro.tweebyte.interactionservice.config;
