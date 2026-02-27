package com.infomaximum.platform.component.frontend.engine.idempotency;

import com.infomaximum.cluster.core.remote.struct.RemoteObject;
import com.infomaximum.platform.component.frontend.request.GRequestHttp;
import com.infomaximum.platform.utils.MapUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.Objects;

public record IdempotencyResponse(GRequestHttp gRequest,
                                  State state,
                                  byte[] responseData,
                                  HttpStatus httpStatus,
                                  HttpHeaders headers) implements RemoteObject {

    public boolean isEqualGRequestHttp(GRequestHttp otherRequest) {
        // Дополнительная защита от коллизий. Если придут разные запросы с одинаковым Idempotency-Key
        return Objects.equals(gRequest.getQuery(), otherRequest.getQuery()) &&
                Objects.equals(gRequest.getOperationName(), otherRequest.getOperationName()) &&
                Objects.equals(gRequest.getIdempotencyKey(), otherRequest.getIdempotencyKey()) &&
                Objects.equals(gRequest.getRemoteAddress().endRemoteAddress, otherRequest.getRemoteAddress().endRemoteAddress) &&
                Objects.equals(gRequest.getQueryVariables(), otherRequest.getQueryVariables()) &&
                MapUtils.equals(gRequest.getParameters(), otherRequest.getParameters()) &&
                MapUtils.equals(gRequest.getAttributes(), otherRequest.getAttributes()) &&
                Objects.deepEquals(gRequest.getCookies(), otherRequest.getCookies());
    }

    public enum State {
        EXECUTE, // Запрос выполняется, результат не получен (responseData == null, httpStatus == null, headers == null)
        READY // Запрос выполнился, результат получен (responseData != null, httpStatus != null, headers != null)
    }
}


