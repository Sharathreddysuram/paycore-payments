package com.sharath.paycore.transfer.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * HTTP implementation of {@link AccountClient} over account-service's internal
 * endpoints, resolved through Eureka via the load-balanced {@code RestClient}
 * ({@code lb://account-service}).
 *
 * <p>Error mapping:
 * <ul>
 *   <li>404 -&gt; {@link AccountNotFoundException}</li>
 *   <li>422 (debit only) -&gt; {@link InsufficientFundsException}</li>
 * </ul>
 * 5xx responses are intentionally NOT handled: {@code retrieve()} throws a
 * {@link org.springframework.web.client.RestClientException} which propagates,
 * so a transfer fails loudly on an account-service outage instead of silently
 * completing.
 */
@Service
public class RestAccountClient implements AccountClient {

    private final RestClient restClient;

    public RestAccountClient(@Qualifier("restClientBuilder") RestClient.Builder builder) {
        this.restClient = builder.baseUrl("lb://account-service").build();
    }

    @Override
    public BalanceResponse debit(String accountNumber, BigDecimal amount) {
        return restClient.post()
                .uri("/internal/accounts/{accountNumber}/debit", accountNumber)
                .body(new AmountRequest(amount))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    if (response.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                        throw new InsufficientFundsException(readMessage(response));
                    }
                    if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                        throw new AccountNotFoundException(accountNumber);
                    }
                    throw new IllegalStateException("account-service debit failed: " + response.getStatusCode());
                })
                .body(BalanceResponse.class);
    }

    @Override
    public BalanceResponse credit(String accountNumber, BigDecimal amount) {
        return restClient.post()
                .uri("/internal/accounts/{accountNumber}/credit", accountNumber)
                .body(new AmountRequest(amount))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                        throw new AccountNotFoundException(accountNumber);
                    }
                    throw new IllegalStateException("account-service credit failed: " + response.getStatusCode());
                })
                .body(BalanceResponse.class);
    }

    /**
     * Extracts the {@code message} from account-service's error body
     * ({@code {"message":"..."}}). Falls back to a generic message when the
     * body cannot be read.
     */
    private String readMessage(ClientHttpResponse response) {
        try {
            byte[] body = StreamUtils.copyToByteArray(response.getBody());
            if (body.length == 0) {
                return "request failed";
            }
            String raw = new String(body, StandardCharsets.UTF_8);
            int idx = raw.indexOf("\"message\"");
            if (idx >= 0) {
                int colon = raw.indexOf(':', idx);
                int firstQuote = raw.indexOf('"', colon + 1);
                int lastQuote = raw.indexOf('"', firstQuote + 1);
                if (firstQuote >= 0 && lastQuote > firstQuote) {
                    return raw.substring(firstQuote + 1, lastQuote);
                }
            }
            return raw;
        } catch (IOException e) {
            return "request failed";
        }
    }
}
