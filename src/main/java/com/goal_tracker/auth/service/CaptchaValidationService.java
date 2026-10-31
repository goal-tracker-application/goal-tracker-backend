package com.goal_tracker.auth.service;

import com.goal_tracker.auth.dto.CaptchaResponseDto;
import com.goal_tracker.auth.exceptions.InvalidCaptchaException;
import com.goal_tracker.config.security.CaptchaProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Service responsible for validating Captcha tokens against external providers
 * (Google reCAPTCHA / Cloudflare Turnstile) using Spring's RestClient.
 */
@Service
public class CaptchaValidationService {

    private final CaptchaProperties captchaProperties;
    private final RestClient restClient;

    /**
     * Constructs the validation service with properties and a pre-configured RestClient.
     *
     * @param captchaProperties configuration properties holding secret key and verify URL
     * @param restClientBuilder builder to instantiate the RestClient
     */
    public CaptchaValidationService(CaptchaProperties captchaProperties, RestClient.Builder restClientBuilder) {
        this.captchaProperties = captchaProperties;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Validates the provided Captcha token against the external provider.
     *
     * @param captchaToken the token sent by the client frontend
     * @throws InvalidCaptchaException if the token is missing, blank, rejected, or communication fails
     */
    public void validateCaptcha(String captchaToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", captchaProperties.getSecretKey());
        body.add("response", captchaToken);

        try {
            ResponseEntity<CaptchaResponseDto> responseEntity = restClient.post()
                    .uri(captchaProperties.getVerifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toEntity(CaptchaResponseDto.class);

            HttpStatusCode statusCode = responseEntity.getStatusCode();
            CaptchaResponseDto response = responseEntity.getBody();

            if (!statusCode.is2xxSuccessful()) {
                throw new InvalidCaptchaException(statusCode);
            }
            else if (response == null || !response.success()){
                throw new InvalidCaptchaException(response);
            }
        } catch (Exception e) {
            if (e instanceof InvalidCaptchaException) {
                throw (InvalidCaptchaException) e;
            }
            throw new InvalidCaptchaException(e);
        }
    }
}