package com.infomaximum.platform.component.frontend.engine.service.errorhandler;

import com.infomaximum.platform.utils.ExceptionUtils;
import jakarta.servlet.ServletException;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.ee10.servlet.Dispatcher;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import com.infomaximum.network.struct.ErrorHandler;
import org.eclipse.jetty.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.util.NestedServletException;

import java.nio.ByteBuffer;
import java.util.List;

public class PlatformErrorHandler implements ErrorHandler {

    private final static Logger log = LoggerFactory.getLogger(PlatformErrorHandler.class);

    private final ActionErrorHandler actionErrorHandler;
    private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;

    public PlatformErrorHandler(
            ActionErrorHandler actionErrorHandler,
            Thread.UncaughtExceptionHandler uncaughtExceptionHandler
    ) {
        this.actionErrorHandler = actionErrorHandler;
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    @Override
    public void handle(Request request, Response response, String errorMessage, Throwable throwable) {
        try {
            if (response.getStatus() == HttpStatus.NOT_FOUND.value()) {
                actionErrorHandler.handlerNotFound(response);
            } else if (response.getStatus() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                ResponseEntity<byte[]> responseEntity = actionErrorHandler.handlerServiceUnavailable();

                response.setStatus(responseEntity.getStatusCodeValue());
                responseEntity.getHeaders().forEach((name, values) -> {
                    response.getHeaders().add(name, values.get(0));
                    for (int i = 1; i < values.size(); i++) {
                        response.getHeaders().add(name, values.get(i));
                    }
                });
                response.write(true, ByteBuffer.wrap(responseEntity.getBody()), Callback.NOOP);
                log.error("SERVICE_UNAVAILABLE", (Throwable) request.getAttribute(Dispatcher.ERROR_EXCEPTION));
            } else if (response.getStatus() >= 400 && response.getStatus() < 500) {
                //Ошибочный запрос от клиентом - игнорируем и прокидываем ответ напрямую
                log.debug("Error request, code: {}, message: {}", response.getStatus(), errorMessage);
            } else {
                if (throwable == null) {
                    throw new RuntimeException("Unknown state errorHandler, response status:" + response.getStatus() + ", " + response);
                } else {
                    throw throwable;
                }
            }
        } catch (Throwable ex) {
            processingException(ex, request, response);
        }
    }

    private void processingException(Throwable ex, Request request, Response response) {
        List<Throwable> chainThrowables = ExceptionUtils.getThrowableList(ex);

        if (ex instanceof EofException) {
            //Обычный разрыв соединения во время передачи данных
            return;
        } else if (chainThrowables.size() == 4
                && chainThrowables.get(0) instanceof jakarta.servlet.ServletException
                && chainThrowables.get(1) instanceof jakarta.servlet.ServletException
                && chainThrowables.get(2) instanceof org.springframework.web.multipart.MultipartException
                && chainThrowables.get(3) instanceof java.io.IOException
        ) {
            //Missing initial multi part boundary
            return;
        } else if (chainThrowables.size() == 5
                && chainThrowables.get(0) instanceof jakarta.servlet.ServletException
                && chainThrowables.get(1) instanceof jakarta.servlet.ServletException
                && chainThrowables.get(2) instanceof org.springframework.web.multipart.MultipartException
                && chainThrowables.get(3) instanceof java.io.IOException
                && chainThrowables.get(4) instanceof java.util.concurrent.TimeoutException
        ) {
            //поставил низкую скорость, начал импортировать в пространство таблицу, "выдернул" сетевой кабель.
            return;
        } else if (chainThrowables.size() == 4
                && chainThrowables.get(0) instanceof jakarta.servlet.ServletException
                && chainThrowables.get(1) instanceof jakarta.servlet.ServletException
                && chainThrowables.get(2) instanceof org.springframework.web.multipart.MultipartException
                && chainThrowables.get(3) instanceof org.eclipse.jetty.io.EofException
        ) {
            //поставил на клиенте медленную скорость, выбрал файл для импорта в то же пространство нажал кнопку загрузки и сразу же кнопку отмены.
            return;
        } else if (chainThrowables.size() == 3
                && chainThrowables.get(1) instanceof NestedServletException
                && chainThrowables.get(2) instanceof IllegalArgumentException
        ) {
            //Exception в случае невалидного url, например:
            // _build/static/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e/%uff0e%uff0e//etc/passwd
            return;
        } else if (chainThrowables.size() == 3
                && chainThrowables.get(0) instanceof ServletException
                && chainThrowables.get(1) instanceof MultipartException
                && chainThrowables.get(2) instanceof EofException
        ) {
            //Разрыв соединение
            return;
        } else if (chainThrowables.size() == 4
                && chainThrowables.get(0) instanceof jakarta.servlet.ServletException
                && chainThrowables.get(1) instanceof org.springframework.web.multipart.MultipartException
                && chainThrowables.get(2) instanceof java.io.IOException
                && chainThrowables.get(3) instanceof java.util.concurrent.TimeoutException
        ) {
            //Разрыв соединение
            return;
        } else if (chainThrowables.size() == 1
                && chainThrowables.get(0) instanceof java.io.IOException
        ) {
            //Разрыв соединение
            return;
        } else if (chainThrowables.size() == 3
                && chainThrowables.get(0) instanceof ServletException
                && chainThrowables.get(1) instanceof MaxUploadSizeExceededException
                && chainThrowables.get(2) instanceof IllegalStateException
        ) {
            //Exception если загружаемый файл превышает по лимитам
            //java.lang.IllegalStateException: Request exceeds maxRequestSize (33554432)
            return;
        } else if (chainThrowables.size() == 3
                && chainThrowables.get(0) instanceof jakarta.servlet.ServletException
                && chainThrowables.get(1) instanceof org.springframework.web.multipart.MultipartException
                && chainThrowables.get(2) instanceof java.io.IOException
        ) {
            //Request processing failed: org.springframework.web.multipart.MultipartException: Failed to parse multipart servlet request
            return;
        } else if (chainThrowables.size() == 1
                && chainThrowables.get(0) instanceof org.eclipse.jetty.http.HttpException httpException
                && httpException.getCode() == 505
        ) {
            //Unsupported HTTP version: org.eclipse.jetty.http.HttpException$RuntimeException: 505: HTTP/0.9 not supported
            return;
        } else if (chainThrowables.size() == 1
                && chainThrowables.get(0) instanceof IllegalStateException
        ) {
            //java.lang.IllegalStateException: Committed
            return;
        }

        String msgException = "Request: " + request.toString() + ", response.status: " + response.getStatus();
        log.error(msgException, ex);

        //Пишем отладочную информацию
        log.error("ChainThrowables, size: {}", chainThrowables.size());
        for (int i = 0; i < chainThrowables.size(); i++) {
            log.error("ChainThrowables, i: {}, exception: {}", i, chainThrowables.get(i).getClass().getName());
        }

        uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), new Exception(msgException, ex));
    }
}
