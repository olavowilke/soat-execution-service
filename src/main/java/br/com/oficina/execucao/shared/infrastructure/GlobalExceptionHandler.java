package br.com.oficina.execucao.shared.infrastructure;

import br.com.oficina.execucao.execucao.entities.TransicaoEtapaInvalidaException;
import br.com.oficina.execucao.execucao.gateways.ExecucaoAlteradaConcorrentementeException;
import br.com.oficina.execucao.execucao.usecases.ExecucaoNaoEncontradaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Traduz exceções em {@link ApiError}. Estende {@link ResponseEntityExceptionHandler}
 * para que as exceções do Spring MVC (rota inexistente, método não permitido,
 * parâmetro ausente etc.) mantenham o status HTTP próprio, em vez de cair no 500 genérico.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.FieldError(e.getField(), e.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of(422, "Unprocessable Entity", "Validation failed", fields));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", "Corpo da requisição inválido ou ilegível"));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String nome = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", "Parâmetro inválido: " + nome));
    }

    /** Demais exceções do Spring MVC: mantém o status e os cabeçalhos (ex.: Allow no 405). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatus http = HttpStatus.resolve(status.value());
        String erro = http != null ? http.getReasonPhrase() : "Error";
        String mensagem = body instanceof ProblemDetail pd && pd.getDetail() != null ? pd.getDetail() : erro;
        if (status.is5xxServerError()) {
            log.error("Erro do servidor", ex);
        }
        return ResponseEntity.status(status).headers(headers)
                .body(ApiError.of(status.value(), erro, mensagem));
    }

    @ExceptionHandler(ExecucaoNaoEncontradaException.class)
    public ResponseEntity<ApiError> handleNaoEncontrada(ExecucaoNaoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler({TransicaoEtapaInvalidaException.class, ExecucaoAlteradaConcorrentementeException.class})
    public ResponseEntity<ApiError> handleConflito(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "Bad Request", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error", "An unexpected error occurred"));
    }
}
