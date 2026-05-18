package com.kakaobank.accounting.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 단위 추적을 위한 MDC 기반 RequestId 필터.
 *
 * 책임:
 *   - 모든 요청에 대해 X-Request-Id 헤더가 있으면 사용하고, 없으면 UUID로 새로 생성합니다.
 *   - MDC에 requestId를 넣어 로그 패턴에 자동으로 포함시킵니다.
 *   - 응답 헤더에도 동일한 requestId를 내려, 클라이언트/게이트웨이가 장애 추적에 활용할 수 있게 합니다.
 *
 * 면접 포인트:
 *   - 로그에 매번 requestId를 수동으로 찍는 대신 MDC를 사용해 누락을 방지합니다.
 *   - finally에서 반드시 clear 합니다. 스레드 풀 재사용 환경에서 이전 요청의 requestId가 남아
 *     다른 요청 로그에 잘못 찍히는 사고를 막기 위해서입니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingRequestId = request.getHeader(HEADER_REQUEST_ID);
        String requestId = (incomingRequestId != null && !incomingRequestId.isBlank())
                ? incomingRequestId
                : UUID.randomUUID().toString();

        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER_REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
