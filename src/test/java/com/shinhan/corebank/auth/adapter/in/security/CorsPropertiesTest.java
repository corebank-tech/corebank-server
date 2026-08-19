package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void normalizesAllowedOrigins() {
        CorsProperties properties = new CorsProperties(List.of(
                " http://localhost:5173 ",
                "",
                "http://localhost:5173"
        ));

        assertThat(properties.allowedOrigins())
                .containsExactly("http://localhost:5173");
    }

    @Test
    void usesEmptyListWhenAllowedOriginsAreNotConfigured() {
        CorsProperties properties = new CorsProperties(null);

        assertThat(properties.allowedOrigins()).isEmpty();
    }
}
