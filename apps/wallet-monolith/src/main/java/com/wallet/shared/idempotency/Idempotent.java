package com.wallet.shared.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as requiring an idempotency key.
 *
 * <p>Clients must supply an {@code Idempotency-Key} header. The system will ensure that exactly one
 * execution occurs for a given key, and subsequent identical requests will receive the cached
 * response.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {}
