package br.com.oficina.execucao.shared.infrastructure;

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
 * Coloca o {@code X-Correlation-Id} da requisição no MDC sob a chave
 * {@code correlationId}. O API Gateway injeta o cabeçalho; sem ele, gera um UUID.
 *
 * <p>{@code X-Cliente-Id} também vai ao MDC, mas só para log. O serviço não usa
 * esse valor para autorização.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CABECALHO_CORRELATION_ID = "X-Correlation-Id";
    public static final String CABECALHO_CLIENTE_ID = "X-Cliente-Id";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_CLIENTE_ID = "clienteId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CABECALHO_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String clienteId = request.getHeader(CABECALHO_CLIENTE_ID);

        try {
            MDC.put(MDC_CORRELATION_ID, correlationId);
            if (clienteId != null && !clienteId.isBlank()) {
                MDC.put(MDC_CLIENTE_ID, clienteId);
            }
            response.setHeader(CABECALHO_CORRELATION_ID, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            // Threads do pool do servlet são reaproveitadas entre requisições:
            // se não limparmos aqui, o correlationId (e o clienteId) desta
            // requisição vazaria para a próxima que a mesma thread atender —
            // inclusive quando a cadeia de filtros lança exceção.
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_CLIENTE_ID);
        }
    }
}
