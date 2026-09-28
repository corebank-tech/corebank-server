package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AdminBootstrapPropertiesTest {

    @Test
    @DisplayName("설정이 없으면 빈 목록이다")
    void usesEmptyListWhenNotConfigured() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties(null);

        assertThat(properties.bootstrapCustomerIds()).isEmpty();
    }

    @Test
    @DisplayName("null 원소와 중복을 걸러낸다")
    void removesNullAndDuplicateIds() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties(Arrays.asList(1L, null, 2L, 1L));

        assertThat(properties.bootstrapCustomerIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("환경변수가 비어 있으면(prod 기본값) 빈 목록으로 바인딩된다")
    void bindsBlankValueToEmptyList() {
        assertThat(bind("").bootstrapCustomerIds()).isEmpty();
    }

    @Test
    @DisplayName("쉼표 구분 값을 바인딩하고 빈 원소는 무시한다")
    void bindsCommaSeparatedIds() {
        assertThat(bind(" 1 ,,2").bootstrapCustomerIds()).containsExactly(1L, 2L);
    }

    private AdminBootstrapProperties bind(String value) {
        Binder binder = new Binder(
                new MapConfigurationPropertySource(Map.of("app.security.admin.bootstrap-customer-ids", value)));
        return binder.bindOrCreate("app.security.admin", AdminBootstrapProperties.class);
    }
}
