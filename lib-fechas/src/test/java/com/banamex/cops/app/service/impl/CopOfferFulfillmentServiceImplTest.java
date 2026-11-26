package com.banamex.cops.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banamex.cops.app.model.CopOfferFulfillment;
import com.banamex.cops.app.repository.CopFulfillmentSuppressionRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

/** Unit tests for {@code CopOfferFulfillmentServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class CopOfferFulfillmentServiceImplTest {

  @Mock CopFulfillmentSuppressionRepository repository;
  @InjectMocks CopOfferFulfillmentServiceImpl service;

  private static Object[] fila() {
    Object[] r = new Object[113];
    r[0] = "CAT";
    r[1] = "TYPE";
    r[2] = "R1";
    r[3] = "Motivo";
    r[4] = "Producto";
    r[5] = "PROC";
    for (int i = 1; i <= 100; i++) {
      r[5 + i] = "uf" + i;
    }
    r[106] = Timestamp.valueOf("2026-10-28 10:42:00");
    r[107] = Timestamp.valueOf("2026-10-28 11:55:00");
    r[108] = Timestamp.valueOf("2026-12-31 00:00:00");
    r[109] = Timestamp.valueOf("2026-01-01 00:00:00");
    r[110] = "PRD";
    r[111] = "FAM";
    r[112] = new BigDecimal("1");
    return r;
  }

  @Test
  void armaElModeloConLasColumnasPorPosicion() {
    // Given
    when(repository.findOfferFulfillment("wvch:W1:CAT", "off:mx:C1:W1:123", PageRequest.of(0, 100)))
        .thenReturn(List.<Object[]>of(fila()));

    // When
    List<CopOfferFulfillment> out =
        service.findByWaveKeyAndOfferKey("wvch:W1:CAT", "off:mx:C1:W1:123");

    // Then
    assertThat(out).hasSize(1);
    CopOfferFulfillment f = out.get(0);
    assertThat(f.crpCategoryId()).isEqualTo("CAT");
    assertThat(f.fulfillmentProcId()).isEqualTo("PROC");
    assertThat(f.userFields()).hasSize(100).startsWith("uf1", "uf2").endsWith("uf100");
    assertThat(f.apptStartDate()).isEqualTo(Timestamp.valueOf("2026-10-28 10:42:00"));
    assertThat(f.apptEndDate()).isEqualTo(Timestamp.valueOf("2026-10-28 11:55:00"));
    assertThat(f.expiryDate()).isEqualTo(Timestamp.valueOf("2026-12-31 00:00:00"));
    assertThat(f.offerCreationDate()).isEqualTo(Timestamp.valueOf("2026-01-01 00:00:00"));
    assertThat(f.prdCode()).isEqualTo("PRD");
    assertThat(f.prdFamily()).isEqualTo("FAM");
    assertThat(f.availability()).isEqualTo("1");
  }

  @Test
  void pideComoMaximo100FilasAndSinFilasDevuelveVacio() {
    // Given
    when(repository.findOfferFulfillment("w", "o", PageRequest.of(0, 100))).thenReturn(List.of());

    // When
    List<CopOfferFulfillment> out = service.findByWaveKeyAndOfferKey("w", "o");

    // Then
    assertThat(out).isEmpty();
    verify(repository).findOfferFulfillment("w", "o", PageRequest.of(0, 100));
  }

  @Test
  void aceptaFechasLocalDateTimeOffsetDateTimeAndNulas() {
    // Given
    Object[] r = fila();
    r[106] = java.time.LocalDateTime.of(2026, 10, 28, 10, 42, 0);
    r[107] = java.time.OffsetDateTime.of(2026, 10, 28, 11, 55, 0, 0, java.time.ZoneOffset.UTC);
    r[108] = null;
    r[109] = new java.util.Date(Timestamp.valueOf("2026-01-01 00:00:00").getTime());
    when(repository.findOfferFulfillment("w", "o", PageRequest.of(0, 100)))
        .thenReturn(List.<Object[]>of(r));

    // When
    CopOfferFulfillment f = service.findByWaveKeyAndOfferKey("w", "o").get(0);

    // Then
    assertThat(f.apptStartDate()).isEqualTo(Timestamp.valueOf("2026-10-28 10:42:00"));
    assertThat(f.apptEndDate())
        .isEqualTo(Timestamp.from(java.time.Instant.parse("2026-10-28T11:55:00Z")));
    assertThat(f.expiryDate()).isNull();
    assertThat(f.offerCreationDate()).isEqualTo(Timestamp.valueOf("2026-01-01 00:00:00"));
  }
}
