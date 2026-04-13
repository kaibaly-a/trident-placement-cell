package com.trident.placement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.util.List;

@Configuration
@Slf4j
public class JwtDecoderConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.audiences}")
    private String audience;

    @Bean
    public JwtDecoder jwtDecoder() {
        log.info("JWT Decoder — expected audience: '{}'", audience);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(issuerUri);

        // ── Audience validator with full debug logging ────────────────────
        // This logs the EXACT aud value from the token so you can see the mismatch
        OAuth2TokenValidator<Jwt> audienceValidator =
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD,
                        aud -> {
                            // Always log what we receive — this shows in your console
                            log.warn("TOKEN AUD VALUE  : {}", aud);
                            log.warn("EXPECTED AUDIENCE: {}", audience);

                            if (aud == null) {
                                log.warn("AUD RESULT: REJECTED — aud claim is null");
                                return false;
                            }

                            boolean matches = aud.contains(audience);
                            log.warn("AUD RESULT: {}", matches ? "ACCEPTED ✓" : "REJECTED ✗ — values do not match");
                            return matches;
                        }
                );

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator)
        );

        return decoder;
    }
}