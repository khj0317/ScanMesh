package com.scanmesh.api.worker.security;

import com.scanmesh.api.worker.service.WorkerService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 워커 API(/api/worker/**)의 X-Worker-Token을 확인한다. 사용자 JWT와 섞이지 않도록 워커 경로에서만 동작하고,
 * 토큰이 없거나 틀리면 인증하지 않은 채로 넘겨 SecurityConfig가 401로 응답하게 한다.
 */
@Component
@RequiredArgsConstructor
public class WorkerAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Worker-Token";
    public static final String PATH_PREFIX = "/api/worker/";

    private final WorkerService workerService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        // 워커 경로에서는 사용자 JWT로 인증된 정보를 쓰지 않는다
        SecurityContextHolder.clearContext();
        String token = request.getHeader(HEADER_NAME);
        if (token != null && !token.isBlank()) {
            workerService.authenticate(token).ifPresent(principal -> {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_WORKER"));
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }
}
