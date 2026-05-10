package com.wallet.shared.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Filter that intercepts requests, checks for {@code @Idempotent} annotations, and enforces the
 * idempotency contract.
 */
@Component
@Order(1) // Runs early in the chain
public class IdempotencyFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
  public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";

  private final IdempotencyService idempotencyService;
  private final RequestHasher requestHasher;
  private final RequestMappingHandlerMapping handlerMapping;

  public IdempotencyFilter(
      IdempotencyService idempotencyService,
      RequestHasher requestHasher,
      @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
          RequestMappingHandlerMapping handlerMapping) {
    this.idempotencyService = idempotencyService;
    this.requestHasher = requestHasher;
    this.handlerMapping = handlerMapping;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    boolean isIdempotentEndpoint = isIdempotent(request);

    if (!isIdempotentEndpoint) {
      filterChain.doFilter(request, response);
      return;
    }

    String idempotencyKey = request.getHeader(HEADER_IDEMPOTENCY_KEY);
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Idempotency-Key header");
      return;
    }

    // Cache the request body so we can read it to hash it, and the controller can read it later
    CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
    ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

    byte[] requestBody = cachedRequest.getInputStream().readAllBytes();
    String requestHash = requestHasher.hash(requestBody);

    try {
      Optional<IdempotencyKeyEntity> existingKey =
          idempotencyService.acquire(idempotencyKey, requestHash);

      if (existingKey.isPresent()) {
        IdempotencyKeyEntity entity = existingKey.get();
        log.info(
            "Idempotency key hit for {}: status={}", idempotencyKey, entity.getResponseStatus());

        if (entity.isCompleted()) {
          responseWrapper.setStatus(entity.getResponseStatus());
          responseWrapper.setContentType(MediaType.APPLICATION_JSON_VALUE);
          if (entity.getResponseBody() != null) {
            responseWrapper.getWriter().write(entity.getResponseBody());
          }
          responseWrapper.copyBodyToResponse();
          return;
        } else {
          response.sendError(
              HttpServletResponse.SC_CONFLICT, "Concurrent request with this key is processing");
          return;
        }
      }

      filterChain.doFilter(cachedRequest, responseWrapper);

      int status = responseWrapper.getStatus();
      if (status >= 200 && status < 500) {
        byte[] responseBodyBytes = responseWrapper.getContentAsByteArray();
        String responseBodyStr = new String(responseBodyBytes, request.getCharacterEncoding());
        if (responseBodyStr.isBlank()) {
          responseBodyStr = null;
        }
        idempotencyService.complete(idempotencyKey, status, responseBodyStr);
      }

    } catch (IdempotencyKeyMismatchException e) {
      response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
    } finally {
      responseWrapper.copyBodyToResponse();
    }
  }

  private boolean isIdempotent(HttpServletRequest request) {
    try {
      HandlerExecutionChain chain = handlerMapping.getHandler(request);
      if (chain != null && chain.getHandler() instanceof HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(Idempotent.class);
      }
    } catch (Exception e) {
      log.warn("Failed to determine if request is idempotent", e);
    }
    return false;
  }

  private static class CachedBodyHttpServletRequest
      extends jakarta.servlet.http.HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
      super(request);
      this.cachedBody = request.getInputStream().readAllBytes();
    }

    @Override
    public jakarta.servlet.ServletInputStream getInputStream() {
      return new jakarta.servlet.ServletInputStream() {
        private final java.io.ByteArrayInputStream bais =
            new java.io.ByteArrayInputStream(cachedBody);

        @Override
        public boolean isFinished() {
          return bais.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener readListener) {
          throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
          return bais.read();
        }
      };
    }

    @Override
    public java.io.BufferedReader getReader() throws IOException {
      return new java.io.BufferedReader(
          new java.io.InputStreamReader(getInputStream(), getCharacterEncoding()));
    }
  }
}
