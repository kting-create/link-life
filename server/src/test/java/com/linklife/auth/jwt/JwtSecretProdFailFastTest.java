package com.linklife.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtSecretProdFailFastTest {

    private static final String DEV_SECRET = "dev-only-secret-key-must-be-at-least-32-bytes!";
    private static final String REAL_SECRET = "prod-secret-value-with-at-least-32-bytes!!";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(JwtService.class)
            .withPropertyValues(
                    "link.jwt.access-ttl-hours=1",
                    "link.jwt.refresh-ttl-days=1");

    @Test
    void prodProfileWithDevSecretFails() {
        runner.withPropertyValues(
                        "spring.profiles.active=prod",
                        "link.jwt.secret=" + DEV_SECRET)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNotNull();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void prodProfileWithRealSecretStarts() {
        runner.withPropertyValues(
                        "spring.profiles.active=prod",
                        "link.jwt.secret=" + REAL_SECRET)
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void devProfileWithDevSecretStarts() {
        runner.withPropertyValues(
                        "link.jwt.secret=" + DEV_SECRET)
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void constructorThreeStateDirect() {
        assertThatThrownBy(() -> new JwtService(DEV_SECRET, "prod", 1, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new JwtService(REAL_SECRET, "prod", 1, 1)).isNotNull();
        assertThat(new JwtService(DEV_SECRET, "dev", 1, 1)).isNotNull();
        assertThat(new JwtService(DEV_SECRET, "", 1, 1)).isNotNull();
    }
}
