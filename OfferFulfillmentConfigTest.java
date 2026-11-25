package com.banamex.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.banamex.cops.app.service.impl.CopOfferFulfillmentServiceImpl;
import com.banamex.cops.app.service.impl.CopSuppressionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Unit tests for {@code OfferFulfillmentConfig}. */
class OfferFulfillmentConfigTest {

  @Test
  void registraLosDosServiciosDeLaLibreria() {
    // Given
    Import importa = OfferFulfillmentConfig.class.getAnnotation(Import.class);

    // When / Then
    assertThat(importa.value())
        .containsExactlyInAnyOrder(
            CopOfferFulfillmentServiceImpl.class, CopSuppressionServiceImpl.class);
  }

  @Test
  void noHabilitaOtraVezLosRepositoriosJpa_loHaceCopsDbAutoConfiguration() {
    // Given
    // (sin precondiciones)

    // When / Then
    assertThat(OfferFulfillmentConfig.class.getAnnotation(EnableJpaRepositories.class)).isNull();
  }
}
