/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.equivalence.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal HTTP client wrapper around HttpURLConnection. Used instead of RestTemplate /
 * WebClient / Apache HttpClient to avoid pulling in any of the dependencies the
 * services-under-test ship — keeps the FE module's classpath independent of either
 * stack's HTTP-client choice.
 *
 * Always speaks to the gateway on https://localhost:8080 (overridable via the
 * `fe.gateway.base.url` system property, set in pom.xml). The prod / functional-
 * equivalence stack runs with edge TLS on, so the dev-CA trust + client identity is
 * installed once via {@link FeTls} before the first request.
 *
 * Records HTTP status, raw body, and parsed JSON onto ScenarioContext so the Then-steps
 * can assert against it.
 */
public final class RestApi {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String BASE = System.getProperty("fe.gateway.base.url", "https://localhost:8080");

	private static final String MULTIPART_BOUNDARY = "----TweebyteFEBoundary" + UUID.randomUUID();

	private static final int READ_TIMEOUT_MS = 180_000;

	static {
		FeTls.install();
	}

	private RestApi() {
	}

	public static String baseUrl() {
		return BASE;
	}

	/** GET {@code path} with optional Bearer token; records response on context. */
	public static void get(String path, String bearer) {
		request("GET", path, bearer, null, null, null);
	}

	/** GET {@code path} with optional Bearer token and an explicit Accept header. */
	public static void getAccept(String path, String bearer, String accept) {
		request("GET", path, bearer, null, null, accept);
	}

	/** POST {@code path} JSON; records response. */
	public static void postJson(String path, String bearer, String jsonBody) {
		request("POST", path, bearer, "application/json", jsonBody.getBytes(StandardCharsets.UTF_8), null);
	}

	/** PUT {@code path} JSON; records response. */
	public static void putJson(String path, String bearer, String jsonBody) {
		request("PUT", path, bearer, "application/json", jsonBody.getBytes(StandardCharsets.UTF_8), null);
	}

	/** POST {@code path} as multipart/form-data (used by /auth/register). */
	public static void postMultipart(String path, String bearer, Map<String, String> fields) {
		byte[] body = buildMultipart(fields);
		String contentType = "multipart/form-data; boundary=" + MULTIPART_BOUNDARY;
		request("POST", path, bearer, contentType, body, null);
	}

	/** POST {@code path} as multipart/form-data with a single binary file part {@code name}. */
	public static void postMultipartFile(String path, String bearer, String name, String filename,
			String fileContentType, byte[] fileBytes) {
		byte[] body = buildMultipartFile(name, filename, fileContentType, fileBytes);
		String contentType = "multipart/form-data; boundary=" + MULTIPART_BOUNDARY;
		request("POST", path, bearer, contentType, body, null);
	}

	/**
	 * POST {@code path} as multipart/form-data with a single binary file part that carries
	 * NO Content-Type sub-header, so the service receives {@code getContentType()==null}
	 * and falls through to {@code APPLICATION_OCTET_STREAM} in {@code MediaService.upload}.
	 */
	public static void postMultipartFileNoContentType(String path, String bearer,
			String name, String filename, byte[] fileBytes) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			out.write(("--" + MULTIPART_BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
			out.write(("Content-Disposition: form-data; name=\"" + name
					+ "\"; filename=\"" + filename + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			out.write(fileBytes);
			out.write("\r\n".getBytes(StandardCharsets.UTF_8));
			out.write(("--" + MULTIPART_BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		String contentType = "multipart/form-data; boundary=" + MULTIPART_BOUNDARY;
		request("POST", path, bearer, contentType, out.toByteArray(), null);
	}

	/** PUT {@code path} as multipart/form-data (used by user-update). */
	public static void putMultipart(String path, String bearer, Map<String, String> fields) {
		byte[] body = buildMultipart(fields);
		String contentType = "multipart/form-data; boundary=" + MULTIPART_BOUNDARY;
		request("PUT", path, bearer, contentType, body, null);
	}

	/** DELETE {@code path} with optional Bearer token. */
	public static void delete(String path, String bearer) {
		request("DELETE", path, bearer, null, null, null);
	}

	private static void request(String method, String path, String bearer, String contentType, byte[] body,
			String accept) {
		ScenarioContext ctx = ScenarioContext.current();
		HttpURLConnection conn = null;
		try {
			URI uri = URI.create(BASE + path);
			conn = (HttpURLConnection) uri.toURL().openConnection();
			conn.setRequestMethod(method);
			conn.setConnectTimeout(5_000);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setRequestProperty("Connection", "close");
			if (bearer != null) {
				conn.setRequestProperty("Authorization", "Bearer " + bearer);
			}
			if (contentType != null) {
				conn.setRequestProperty("Content-Type", contentType);
			}
			conn.setRequestProperty("Accept", (accept != null) ? accept : "application/json");
			if (body != null) {
				conn.setDoOutput(true);
				try (OutputStream out = conn.getOutputStream()) {
					out.write(body);
				}
			}

			ctx.lastStatus = conn.getResponseCode();
			byte[] bodyBytes = readBody(conn);
			ctx.lastBodyByteLength = bodyBytes.length;
			ctx.lastBody = new String(bodyBytes, StandardCharsets.UTF_8);
			ctx.lastJson = parseJsonOrNull(ctx.lastBody);
			ctx.lastContentType = conn.getContentType();
			conn.disconnect();
		}
		catch (IOException ex) {
			throw new RuntimeException(method + " " + path + " failed: " + ex.getMessage(), ex);
		}
		finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	private static byte[] readBody(HttpURLConnection conn) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		try (var in = (conn.getResponseCode() < 400) ? conn.getInputStream() : conn.getErrorStream()) {
			if (in == null) {
				return new byte[0];
			}
			in.transferTo(buf);
		}
		return buf.toByteArray();
	}

	private static JsonNode parseJsonOrNull(String body) {
		if (body == null || body.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readTree(body);
		}
		catch (IOException | RuntimeException ex) {
			return null;
		}
	}

	private static byte[] buildMultipart(Map<String, String> fields) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			for (Map.Entry<String, String> field : fields.entrySet()) {
				out.write(("--" + MULTIPART_BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
				out.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n")
					.getBytes(StandardCharsets.UTF_8));
				out.write(field.getValue().getBytes(StandardCharsets.UTF_8));
				out.write("\r\n".getBytes(StandardCharsets.UTF_8));
			}
			out.write(("--" + MULTIPART_BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		return out.toByteArray();
	}

	private static byte[] buildMultipartFile(String name, String filename, String fileContentType, byte[] fileBytes) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			out.write(("--" + MULTIPART_BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
			out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
				.getBytes(StandardCharsets.UTF_8));
			out.write(("Content-Type: " + fileContentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			out.write(fileBytes);
			out.write("\r\n".getBytes(StandardCharsets.UTF_8));
			out.write(("--" + MULTIPART_BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		return out.toByteArray();
	}

	/** URL-encode a single path segment. */
	public static String segment(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
	}

}
