package com.banamex.app.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.banamex.app.config.UpdateOfferResponseProperties;
import com.banamex.app.kafka.EventDestinations;
import com.banamex.app.kafka.KafkaMetrics;
import com.banamex.app.kafka.OfferDataClient;
import com.banamex.app.kafka.OfferInput;
import com.banamex.app.kafka.OfferKafkaProps;
import com.banamex.app.kafka.OfferKeyBuilder;
import com.banamex.app.kafka.OfferPublisher;
import com.banamex.app.service.UpdateOfferResponseFactory;
import com.banamex.cops.app.model.CopOfferFulfillment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.util.ReflectionTestUtils;

/** Pruebas unitarias de {@code OfferFulfillmentEmsSender}. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OfferFulfillmentEmsSenderTest {

  private static final String WAVE_KEY = "wvch:W1:CAT";
  private static final String OFFER_KEY = "off:mx:C1:W1:123:ACC:1234567812345678";
  private static final String REF_ID = "C1_W1_123_ACC_1234567812345678";
  private static final String OFFSET_MS =
      "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}([+-]\\d{2}:\\d{2}|Z)";
  private static final String OFFSET_S =
      "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}([+-]\\d{2}:\\d{2}|Z)";
  private static final Timestamp START = Timestamp.valueOf("2026-10-25 10:30:00");
  private static final Timestamp EXPIRY = Timestamp.valueOf("2026-12-31 00:00:00");
  private static final Timestamp CREATED = Timestamp.valueOf("2026-10-27 17:53:02");

  @Mock OfferDataClient dataClient;
  @Mock OfferPublisher publisher;
  @Mock TaskExecutor executor;

  private final JsonMapper mapper = JsonMapper.builder().build();
  private OfferKafkaProps props;
  private KafkaMetrics metrics;
  private OfferFulfillmentEmsSender sender;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;
  private UpdateOfferResponseFactory responses;

  @BeforeEach
  void setUp() throws Exception {
    props = new OfferKafkaProps();
    props.setDelayMs(0);
    props.setMaxAttempts(2);
    props.setRetryBackoffMs(0);
    metrics = new KafkaMetrics();
    responses = new UpdateOfferResponseFactory(new UpdateOfferResponseProperties());
    ReflectionTestUtils.setField(
        responses, "clock", Clock.fixed(Instant.parse("2026-11-11T16:30:45Z"), ZoneOffset.UTC));
    sender =
        new OfferFulfillmentEmsSender(
            props,
            new OfferKeyBuilder(props),
            dataClient,
            EventDestinations.of(
                new EventDestinations.EventDestination("KAFKA", "Kafka", publisher)),
            mapper,
            metrics,
            executor,
            responses);

    when(dataClient.find(eq(WAVE_KEY), eq(OFFER_KEY))).thenReturn(Optional.of(dbRow()));
    when(publisher.publish(anyString(), anyString())).thenReturn("id-1");

    logger = (Logger) LoggerFactory.getLogger(OfferFulfillmentEmsSender.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
  }

  private List<String> mensajes() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  private OfferInput input() {
    return OfferInput.builder()
        .uuid("uuid-1")
        .esbUuid("esb-1")
        .channel("CAT")
        .transactionTime("2026-10-26T10:00:00.123")
        .refId(REF_ID)
        .offerKey(OFFER_KEY)
        .campaignId("C1")
        .waveId("W1")
        .customerNo("123")
        .accountNo("ACC")
        .cardNumber("1234567812345678")
        .offerStatus("H")
        .actionId("PROCESSCALL_ON_AUTHENTICATION_SUCCESS_FULFILLMENT")
        .responseTime("2026-10-26T10:00:01.456-06:00")
        .responseCode("COP_UPDOF_0")
        .responseText("Successfully update offer " + OFFER_KEY)
        .build();
  }

  private CopOfferFulfillment dbRow() {
    List<String> userFields = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {
      userFields.add(null);
    }
    userFields.set(0, "ORO BANAMEX");
    userFields.set(4, "15000");
    return new CopOfferFulfillment(
        "CAT1",
        "T",
        "R1",
        "motivo",
        "DISPONIBLE SPEI",
        "PDSPPEI",
        userFields,
        START,
        Timestamp.valueOf("2026-10-25 11:00:00"),
        EXPIRY,
        CREATED,
        "DISPONIBLE_SPEI",
        "F1",
        "0");
  }

  private String publicadoRaw() throws Exception {
    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(publisher).publish(eq(REF_ID), json.capture());
    return json.getValue();
  }

  private JsonNode publicado() throws Exception {
    return mapper.readTree(publicadoRaw());
  }

  private static void enOrden(String raw, String... claves) {
    int anterior = -1;
    for (String k : claves) {
      int i = raw.indexOf("\"" + k + "\"");
      assertThat(i).as("falta " + k).isGreaterThanOrEqualTo(0);
      assertThat(i).as(k + " fuera de orden").isGreaterThan(anterior);
      anterior = i;
    }
  }

  @Test
  void flujoCompletoConsultaPorLaClaveDeLaOfertaAndPublica() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    verify(dataClient).find(WAVE_KEY, OFFER_KEY);
    assertThat(publicado().has("event")).isTrue();
    assertThat(metrics.getSent()).isEqualTo(1);
  }

  @Test
  void headerLlevaLosDatosAndLasConstantesDelEjemplo() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    JsonNode h = publicado().get("event").get("header");

    assertThat(h.get("name").asText()).isEqualTo("Updateofferrequest");
    assertThat(h.get("version").asText()).isEqualTo("1.0");
    assertThat(h.get("producerCSI").asText()).isEqualTo("163621");
    assertThat(h.get("Channel").asText()).isEqualTo("CAT");
    assertThat(h.get("countryCode").asText()).isEqualTo("MX");
    assertThat(h.get("businessCode").asText()).isEqualTo("DM");
    assertThat(h.get("UUID").asText()).isEqualTo("uuid-1");
    assertThat(h.get("transactionTime").asText()).isEqualTo("2026-10-26T10:00:00.123");
  }

  @Test
  void sidEsElEsbUuidGeneradoNoElUuidDelHeader() throws Exception {
    // Given
    // (sin precondiciones propias: input() trae uuid-1 y esbUuid esb-1)

    // When
    sender.process(input());

    // Then
    JsonNode h = publicado().get("event").get("header");
    assertThat(h.get("sid").asText()).isEqualTo("esb-1");
    assertThat(h.get("UUID").asText()).isEqualTo("uuid-1");
  }

  @Test
  void sidSinEsbUuidUsaElUuid() throws Exception {
    // Given
    OfferInput in = input();
    in.setEsbUuid(null);

    // When
    sender.process(in);

    // Then
    assertThat(publicado().get("event").get("header").get("sid").asText()).isEqualTo("uuid-1");
  }

  @Test
  void headerMismoOrdenQueElEjemplo() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    enOrden(
        publicadoRaw(),
        "name",
        "version",
        "producerCSI",
        "Channel",
        "countryCode",
        "businessCode",
        "UUID",
        "sid",
        "transactionTime",
        "timeStamp");
  }

  @Test
  void timeStampEsLaHoraActualConMilisegundosAndOffset() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    assertThat(publicado().get("event").get("header").get("timeStamp").asText()).matches(OFFSET_MS);
  }

  @Test
  void timeStampUsaLaZonaEcrmConfigurada() throws Exception {
    // Given
    org.springframework.test.util.ReflectionTestUtils.setField(
        sender, "ecrmTimeZone", "Pacific/Honolulu");

    // When
    sender.process(input());

    // Then
    assertThat(publicado().get("event").get("header").get("timeStamp").asText()).endsWith("-10:00");
  }

  @Test
  void timeStampPorDefectoUsaUsCentral() throws Exception {
    // Given
    // (sin precondiciones: la zona por defecto es la de las globales)

    // When
    sender.process(input());

    // Then
    assertThat(publicado().get("event").get("header").get("timeStamp").asText())
        .matches(".*(-05:00|-06:00)$");
  }

  @Test
  void transactionTimeVacioSeLlenaConLaHoraActualSinOffset() throws Exception {
    // Given
    OfferInput in = input();
    in.setTransactionTime("");

    // When
    sender.process(in);

    // Then
    assertThat(publicado().get("event").get("header").get("transactionTime").asText())
        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}");
  }

  @Test
  void bodyLlevaLosDatosDeLaEntradaAndDeLaBd() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    JsonNode b = publicado().get("event").get("body");

    assertThat(b.get("RefID").asText()).isEqualTo(REF_ID);
    assertThat(b.get("CampaignID").asText()).isEqualTo("C1");
    assertThat(b.get("WaveID").asText()).isEqualTo("W1");
    assertThat(b.get("AccountNo").asText()).isEqualTo("ACC");
    assertThat(b.get("CardNumber").asText()).isEqualTo("1234567812345678");
    assertThat(b.get("OfferStatus").asText()).isEqualTo("H");
    assertThat(b.get("Action_ID").asText())
        .isEqualTo("PROCESSCALL_ON_AUTHENTICATION_SUCCESS_FULFILLMENT");
    assertThat(b.get("ResponseTime").asText()).isEqualTo("2026-10-26T10:00:01.456-06:00");
    assertThat(b.get("ResponseCode").asText()).isEqualTo("COP_UPDOF_0");
    assertThat(b.get("ResponseText").asText()).isEqualTo("Successfully update offer " + OFFER_KEY);

    assertThat(b.get("Product_Description").asText()).isEqualTo("DISPONIBLE SPEI");
    assertThat(b.get("FulfillmentID").asText()).isEqualTo("PDSPPEI");
    assertThat(b.get("PrdCode").asText()).isEqualTo("DISPONIBLE_SPEI");
    assertThat(b.get("Availability").asText()).isEqualTo("0");
  }

  @Test
  void bodyMismoOrdenQueElEjemplo() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    enOrden(
        publicadoRaw(),
        "RefID",
        "CampaignID",
        "WaveID",
        "CustomerNo",
        "AccountNo",
        "CardNumber",
        "OfferStatus",
        "ID_ProductType",
        "CRP_Category_ID",
        "Product_Description",
        "Reason_Code",
        "Reason_Description",
        "Action_ID",
        "ResponseTime",
        "ResponseCode",
        "ResponseText",
        "FulfillmentID",
        "UserField1",
        "UserField5",
        "AppointmentDate",
        "AppointmentMinDur",
        "OfferExpiryDate",
        "OfferStartDate",
        "PrdCode",
        "PrdFamily",
        "Availability");
  }

  @Test
  void bodyLasFechasDeLaBdsalenConOffsetAndSinFracciones() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    JsonNode b = publicado().get("event").get("body");

    assertThat(b.get("OfferExpiryDate").asText())
        .matches(OFFSET_S)
        .startsWith("2026-12-31T00:00:00");
    assertThat(b.get("OfferStartDate").asText())
        .matches(OFFSET_S)
        .startsWith("2026-10-27T17:53:02");
  }

  @Test
  void bodyLlevaLosCamposDelEsquemaQueLlenaLaBd() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    JsonNode b = publicado().get("event").get("body");
    assertThat(b.get("ID_ProductType").asText()).isEqualTo("T");
    assertThat(b.get("CRP_Category_ID").asText()).isEqualTo("CAT1");
    assertThat(b.get("Reason_Code").asText()).isEqualTo("R1");
    assertThat(b.get("Reason_Description").asText()).isEqualTo("motivo");
    assertThat(b.get("PrdFamily").asText()).isEqualTo("F1");
    assertThat(b.get("AppointmentDate").asText())
        .matches(OFFSET_S)
        .startsWith("2026-10-25T10:30:00");
  }

  @Test
  void appointmentMinDurEsLaDuracionDeLaCitaEnMinutos() throws Exception {
    // Given
    // (sin precondiciones propias: la cita de prueba va de 10:30 a 11:00)

    // When
    sender.process(input());

    // Then
    assertThat(publicado().get("event").get("body").get("AppointmentMinDur").asLong())
        .isEqualTo(30L);
  }

  @Test
  void appointmentMinutesSeOmiteSiFaltaUnaFechaOrSiNoEsMayorQueCero() {
    // Given
    Timestamp inicio = Timestamp.valueOf("2026-10-25 10:30:00");

    // When / Then
    assertThat(
            OfferFulfillmentEmsSender.appointmentMinutes(
                inicio, Timestamp.valueOf("2026-10-25 11:00:00")))
        .isEqualTo(30L);
    assertThat(
            OfferFulfillmentEmsSender.appointmentMinutes(
                inicio, Timestamp.valueOf("2026-10-25 10:30:20")))
        .isNull();
    assertThat(OfferFulfillmentEmsSender.appointmentMinutes(inicio, inicio)).isNull();
    assertThat(
            OfferFulfillmentEmsSender.appointmentMinutes(
                inicio, Timestamp.valueOf("2026-10-25 10:00:00")))
        .isNull();
    assertThat(OfferFulfillmentEmsSender.appointmentMinutes(null, inicio)).isNull();
    assertThat(OfferFulfillmentEmsSender.appointmentMinutes(inicio, null)).isNull();
  }

  @Test
  void bodyNoLlevaCamposConNombresQueNoSonDelEsquema() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    JsonNode b = publicado().get("event").get("body");
    for (String campo :
        List.of(
            "ReasonCode",
            "ReasonDesc",
            "ProductType",
            "ProductFamily",
            "CategoryID",
            "AppointmentStartDate",
            "ProductCode",
            "ExpiryDate",
            "OfferCreationDate",
            "FulfillmentProcID",
            "ProductDescription")) {
      assertThat(b.has(campo)).as(campo).isFalse();
    }
  }

  @Test
  void siLaCitaNoTieneFechasNoSeEnvianAppointmentDateNiAppointmentMinDur() throws Exception {
    // Given
    CopOfferFulfillment sinCita =
        new CopOfferFulfillment(
            "CAT1",
            "T",
            "R1",
            "motivo",
            "DISPONIBLE SPEI",
            "PDSPPEI",
            dbRow().userFields(),
            null,
            null,
            EXPIRY,
            CREATED,
            "DISPONIBLE_SPEI",
            "F1",
            "0");
    when(dataClient.find(eq(WAVE_KEY), eq(OFFER_KEY))).thenReturn(Optional.of(sinCita));

    // When
    sender.process(input());

    // Then
    JsonNode b = publicado().get("event").get("body");
    assertThat(b.has("AppointmentDate")).isFalse();
    assertThat(b.has("AppointmentMinDur")).isFalse();
  }

  @Test
  void actionIdSeOmiteSiElRequestNoTraeOfferAction() throws Exception {
    // Given
    OfferInput in = input();
    in.setActionId(null);

    // When
    sender.process(in);

    // Then
    assertThat(publicado().get("event").get("body").has("Action_ID")).isFalse();
  }

  @Test
  void userFieldsVienenDeLaBdylosNulosSeOmiten() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    JsonNode b = publicado().get("event").get("body");

    assertThat(b.get("UserField1").asText()).isEqualTo("ORO BANAMEX");
    assertThat(b.get("UserField5").asText()).isEqualTo("15000");
    assertThat(b.has("UserField2")).isFalse();
    assertThat(b.has("UserField100")).isFalse();
  }

  @Test
  void customerNoSeRellenaConCerosA9DespuesDeLaConsulta() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    assertThat(publicado().get("event").get("body").get("CustomerNo").asText())
        .isEqualTo("000000123");
    verify(dataClient).find(WAVE_KEY, OFFER_KEY);
  }

  @Test
  void customerNoDe9OrMasDigitosNoSeModifica() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When / Then
    assertThat(OfferFulfillmentEmsSender.padLeft("067799015", 9)).isEqualTo("067799015");
    assertThat(OfferFulfillmentEmsSender.padLeft("1234567890", 9)).isEqualTo("1234567890");
    assertThat(OfferFulfillmentEmsSender.padLeft(null, 9)).isNull();
  }

  @Test
  void maskCardNumberDejaSoloLosUltimosCuatroDigitos() throws Exception {
    // Given
    props.setMaskCardNumber(true);

    // When
    sender.process(input());

    // Then
    assertThat(publicado().get("event").get("body").get("CardNumber").asText())
        .isEqualTo("************5678");
  }

  @Test
  void sinMascaraLaTarjetaVaCompletaComoEnElEjemplo() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    assertThat(publicado().get("event").get("body").get("CardNumber").asText())
        .isEqualTo("1234567812345678");
  }

  @Test
  void maskCasosBorde() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When / Then
    assertThat(OfferFulfillmentEmsSender.mask("1234")).isEqualTo("1234");
    assertThat(OfferFulfillmentEmsSender.mask(null)).isNull();
    assertThat(OfferFulfillmentEmsSender.mask("123456")).isEqualTo("**3456");
  }

  @Test
  void isoConvierteTimestampAsegundosConOffset() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When / Then
    assertThat(OfferFulfillmentEmsSender.iso(START))
        .matches(OFFSET_S)
        .startsWith("2026-10-25T10:30:00");
    assertThat(OfferFulfillmentEmsSender.iso("2026-01-01")).isEqualTo("2026-01-01");
    assertThat(OfferFulfillmentEmsSender.iso(null)).isNull();
  }

  @Test
  void omitNullsFalsoLosNulosSeEscribenComoNull() throws Exception {
    // Given
    props.setOmitNulls(false);
    OfferInput in = input();
    in.setActionId(null);

    // When
    sender.process(in);

    // Then
    JsonNode b = publicado().get("event").get("body");
    assertThat(b.has("Action_ID")).isTrue();
    assertThat(b.get("Action_ID").isNull()).isTrue();
    assertThat(b.has("UserField2")).isTrue();
  }

  @Test
  void sinFilasEnLaBdconSendWithoutDbRowsApagadoDescartaAndNoPublica() throws Exception {
    // Given
    props.setSendWithoutDbRows(false);
    when(dataClient.find(eq(WAVE_KEY), eq(OFFER_KEY))).thenReturn(Optional.empty());

    // When
    sender.process(input());

    // Then
    verifyNoInteractions(publisher);
    assertThat(metrics.getNoRows()).isEqualTo(1);
    assertThat(metrics.getFailed()).isZero();
    verify(dataClient, times(1)).find(eq(WAVE_KEY), eq(OFFER_KEY));
  }

  @Test
  void sinFilasEnLaBdporDefectoPublicaConLosDatosDelRequest() throws Exception {
    // Given
    when(dataClient.find(eq(WAVE_KEY), eq(OFFER_KEY))).thenReturn(Optional.empty());

    // When
    sender.process(input());

    // Then
    JsonNode body = publicado().get("event").get("body");
    assertThat(body.get("RefID").asText()).isEqualTo(REF_ID);
    assertThat(body.get("OfferStatus").asText()).isEqualTo("H");
    assertThat(body.has("Reason_Code")).isFalse();
    assertThat(metrics.getNoRows()).isEqualTo(1);
  }

  @Test
  void camposObligatoriosFaltantesDescartaSinConsultarLaBd() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    for (String campo :
        new String[] {"refId", "offerKey", "campaignId", "waveId", "customerNo", "offerStatus"}) {
      OfferInput in = input();
      switch (campo) {
        case "refId" -> in.setRefId(null);
        case "offerKey" -> in.setOfferKey("");
        case "campaignId" -> in.setCampaignId("");
        case "waveId" -> in.setWaveId(" ");
        case "customerNo" -> in.setCustomerNo(null);
        default -> in.setOfferStatus("");
      }
      sender.process(in);
    }

    // Then
    verifyNoInteractions(dataClient, publisher);
    assertThat(metrics.getInvalid()).isEqualTo(6);
  }

  @Test
  void fallaDeInfraestructuraEnLaBdseReintentaAndSiPersisteSeCuentaComoFallida() throws Exception {
    // Given
    when(dataClient.find(eq(WAVE_KEY), eq(OFFER_KEY))).thenThrow(new RuntimeException("db caída"));

    // When
    sender.process(input());

    // Then
    verify(dataClient, times(2)).find(eq(WAVE_KEY), eq(OFFER_KEY));
    verifyNoInteractions(publisher);
    assertThat(metrics.getFailed()).isEqualTo(1);
    assertThat(metrics.getSent()).isZero();
  }

  @Test
  void siElSegundoIntentoFuncionaPublica() throws Exception {
    // Given
    when(dataClient.find(eq(WAVE_KEY), eq(OFFER_KEY)))
        .thenThrow(new RuntimeException("timeout"))
        .thenReturn(Optional.of(dbRow()));

    // When
    sender.process(input());

    // Then
    verify(dataClient, times(2)).find(eq(WAVE_KEY), eq(OFFER_KEY));
    verify(publisher).publish(anyString(), anyString());
    assertThat(metrics.getSent()).isEqualTo(1);
    assertThat(metrics.getFailed()).isZero();
  }

  @Test
  void fallaAlPublicarSeReintentaAndSeCuentaComoFallida() throws Exception {
    // Given
    when(publisher.publish(anyString(), anyString())).thenThrow(new RuntimeException("cola caída"));

    // When
    sender.process(input());

    // Then
    verify(publisher, times(2)).publish(anyString(), anyString());
    assertThat(metrics.getFailed()).isEqualTo(1);
    assertThat(metrics.getSent()).isZero();
  }

  @Test
  void processNuncaLanzaExcepcion() {
    // Given
    when(dataClient.find(anyString(), anyString()))
        .thenThrow(new IllegalStateException("inesperado"));

    // When
    sender.process(input());

    // Then
    assertThat(metrics.getFailed()).isEqualTo(1);
  }

  @Test
  void sendAsyncEncolaElTrabajoEnElEjecutorAndAlCorrerloSePublica() throws Exception {
    // Given
    doAnswer(
            inv -> {
              ((Runnable) inv.getArgument(0)).run();
              return null;
            })
        .when(executor)
        .execute(any(Runnable.class));

    // When
    sender.sendAsync(input());

    // Then
    verify(executor).execute(any(Runnable.class));
    verify(publisher).publish(anyString(), anyString());
  }

  @Test
  void sendAsyncNoEjecutaNadaEnElHiloLlamador() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.sendAsync(input());

    // Then
    verify(executor).execute(any(Runnable.class));
    verify(publisher, never()).publish(anyString(), anyString());
  }

  @Test
  void sendAsyncConColaLlenaCuentaElRechazoAndNoLanzaExcepcion() {
    // Given
    doThrow(new TaskRejectedException("cola llena")).when(executor).execute(any(Runnable.class));

    // When
    sender.sendAsync(input());

    // Then
    assertThat(metrics.getRejected()).isEqualTo(1);
  }

  @Test
  void isEnabledReflejaLaConfiguracion() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When / Then
    props.setEnabled(false);
    assertThat(sender.isEnabled()).isFalse();
    props.setEnabled(true);
    assertThat(sender.isEnabled()).isTrue();
  }

  @Test
  void sinRespuestaDelEmsElEventoSeArmaAndSePublicaIgualSinResponseCodeNiResponseText()
      throws Exception {
    // Given
    props.setMode("jms");
    OfferInput in = input();
    in.setResponseCode(null);
    in.setResponseText(null);

    // When
    sender.process(in);

    // Then
    verify(dataClient).find(eq(WAVE_KEY), eq(OFFER_KEY));
    verify(publisher).publish(eq(REF_ID), anyString());
    JsonNode body = publicado().get("event").get("body");
    assertThat(body.has("ResponseCode")).isFalse();
    assertThat(body.has("ResponseText")).isFalse();
    assertThat(resultado()).startsWith("[KAFKA] Resultado: Successfully send offer to Kafka");
  }

  @Test
  void conResponseCodeVacioTambienSePublica() throws Exception {
    // Given
    OfferInput in = input();
    in.setResponseCode("   ");

    // When
    sender.process(in);

    // Then
    verify(publisher).publish(eq(REF_ID), anyString());
  }

  private String resultado() {
    return mensajes().stream()
        .filter(m -> m.startsWith("[KAFKA] Resultado:"))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void resultadoSiSePublicoDicePublicadoConLaFechaDeLaZonaEcrm() {
    // Given
    props.setMode("jms");

    // When
    sender.process(input());

    // Then
    assertThat(resultado())
        .isEqualTo("[KAFKA] Resultado: Successfully send offer to Kafka 2026/11/11 10:30:45");
  }

  @Test
  void resultadoEnDryRunDiceQueNoSePublico() {
    // Given
    props.setMode("dry-run");

    // When
    sender.process(input());

    // Then
    assertThat(resultado())
        .isEqualTo(
            "[KAFKA] Resultado: Kafka en dry-run: el mensaje no se publicó 2026/11/11 10:30:45");
  }

  @Test
  void resultadoSinFilasEnLaBddiceEnQuePasoFalloAndLasClavesBuscadas() {
    // Given
    props.setSendWithoutDbRows(false);
    when(dataClient.find(eq(WAVE_KEY), eq(OFFER_KEY))).thenReturn(Optional.empty());

    // When
    sender.process(input());

    // Then
    assertThat(resultado())
        .startsWith(
            "[KAFKA] Resultado: Error send offer to Kafka 2026/11/11 10:30:45. Falló en Consulta a"
                + " la base de datos:")
        .contains("no se encontró la oferta")
        .contains("waveKey=" + WAVE_KEY)
        .contains("offerKey=" + OFFER_KEY);
    verify(publisher, never()).publish(anyString(), anyString());
  }

  @Test
  void resultadoSiFallaLaConsultaDiceElPasoAndLaCausa() throws Exception {
    // Given
    when(dataClient.find(eq(WAVE_KEY), eq(OFFER_KEY)))
        .thenThrow(new IllegalStateException("ORA-00904"));

    // When
    sender.process(input());

    // Then
    assertThat(resultado()).contains("Falló en Consulta a la base de datos: ORA-00904");
  }

  @Test
  void resultadoSiFallaLaPublicacionDiceElPasoAndLaCausa() throws Exception {
    // Given
    when(publisher.publish(anyString(), anyString()))
        .thenThrow(new IllegalStateException("sin conexión al broker"));

    // When
    sender.process(input());

    // Then
    assertThat(resultado())
        .contains("Error send offer to Kafka 2026/11/11 10:30:45")
        .contains("Falló en Publicación en la cola de Kafka: sin conexión al broker");
  }

  @Test
  void resultadoConEntradaInvalidaDiceQueFaltanCampos() {
    // Given
    OfferInput in = input();
    in.setOfferStatus("");

    // When
    sender.process(in);

    // Then
    assertThat(resultado())
        .contains("Falló en Validación del evento")
        .contains("faltan campos obligatorios");
  }

  @Test
  void resultadoConColaLlenaDiceQueNoSeEnvio() {
    // Given
    doThrow(new TaskRejectedException("cola llena")).when(executor).execute(any(Runnable.class));

    // When
    sender.sendAsync(input());

    // Then
    assertThat(resultado()).contains("Falló en Cola de envío llena");
  }

  @Test
  void resultadoSiSeInterrumpeDiceQueSeInterrumpio() {
    // Given
    props.setDelayMs(1);
    Thread.currentThread().interrupt();

    // When
    try {
      sender.process(input());
    } finally {
      Thread.interrupted();
    }

    // Then
    assertThat(resultado())
        .contains("Falló en Espera previa a la consulta")
        .contains("interrumpió");
  }

  @Test
  void resultadoSeEscribeUnaSolaVezPorEnvio() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    assertThat(mensajes().stream().filter(m -> m.startsWith("[KAFKA] Resultado:")).count())
        .isEqualTo(1);
  }

  @Test
  void registraElMensajeQueSeLeVaAenviarConLaTarjetaEnmascarada() throws Exception {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    sender.process(input());

    // Then
    String envia =
        mensajes().stream().filter(m -> m.startsWith("[KAFKA] Envía:")).findFirst().orElseThrow();
    assertThat(envia).contains("\"name\":\"Updateofferrequest\"", "\"RefID\":\"" + REF_ID + "\"");
    assertThat(envia)
        .contains("\"CardNumber\":\"************5678\"")
        .doesNotContain("\"CardNumber\":\"1234567812345678\"");
    assertThat(publicadoRaw()).contains("\"CardNumber\":\"1234567812345678\"");
  }

  @Test
  void maskCardInJsonCasosBorde() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When / Then
    assertThat(
            OfferFulfillmentEmsSender.maskCardInJson(
                "{\"CardNumber\":\"1234567812345678\",\"x\":1}"))
        .isEqualTo("{\"CardNumber\":\"************5678\",\"x\":1}");
    assertThat(OfferFulfillmentEmsSender.maskCardInJson("{\"CardNumber\" : \"1234\"}"))
        .isEqualTo("{\"CardNumber\" : \"1234\"}");
    assertThat(OfferFulfillmentEmsSender.maskCardInJson("{\"a\":1}")).isEqualTo("{\"a\":1}");
  }

  // ------------------------------------------------------------ varios destinos (Kafka y EMS
  // alterno)

  private OfferFulfillmentEmsSender conDosDestinos(OfferPublisher kafka, OfferPublisher alterno) {
    return new OfferFulfillmentEmsSender(
        props,
        new OfferKeyBuilder(props),
        dataClient,
        EventDestinations.of(
            new EventDestinations.EventDestination("KAFKA", "Kafka", kafka),
            new EventDestinations.EventDestination("EMS-ALTERNO", "EMS alterno", alterno)),
        mapper,
        metrics,
        executor,
        responses);
  }

  @Test
  void conDosDestinosPublicaEnCadaUnoAndEscribeUnResultadoPorDestino() throws Exception {
    // Given
    props.setMode("jms");
    OfferPublisher alterno = org.mockito.Mockito.mock(OfferPublisher.class);

    // When
    conDosDestinos(publisher, alterno).process(input());

    // Then
    verify(publisher).publish(eq(REF_ID), anyString());
    verify(alterno).publish(eq(REF_ID), anyString());
    assertThat(mensajes())
        .contains(
            "[KAFKA] Resultado: Successfully send offer to Kafka 2026/11/11 10:30:45",
            "[EMS-ALTERNO] Resultado: Successfully send offer to EMS alterno 2026/11/11 10:30:45");
    assertThat(mensajes()).anyMatch(m -> m.startsWith("[KAFKA+EMS-ALTERNO] Envía: "));
  }

  @Test
  void siFallaUnDestinoElOtroSeEnviaIgual() throws Exception {
    // Given
    props.setMode("jms");
    props.setMaxAttempts(1);
    OfferPublisher alterno = org.mockito.Mockito.mock(OfferPublisher.class);
    when(publisher.publish(anyString(), anyString()))
        .thenThrow(new IllegalStateException("bridge caído"));

    // When
    conDosDestinos(publisher, alterno).process(input());

    // Then
    verify(alterno).publish(eq(REF_ID), anyString());
    assertThat(mensajes())
        .anyMatch(
            m ->
                m.startsWith("[KAFKA] Resultado: Error send offer to Kafka")
                    && m.contains("bridge caído"));
    assertThat(mensajes())
        .contains(
            "[EMS-ALTERNO] Resultado: Successfully send offer to EMS alterno 2026/11/11 10:30:45");
  }

  @Test
  void soloEmsAlternoLosMensajesDicenEmsAlterno() throws Exception {
    // Given
    props.setMode("jms");
    props.setSendWithoutDbRows(false);
    OfferFulfillmentEmsSender soloAlterno =
        new OfferFulfillmentEmsSender(
            props,
            new OfferKeyBuilder(props),
            dataClient,
            EventDestinations.of(
                new EventDestinations.EventDestination("EMS-ALTERNO", "EMS alterno", publisher)),
            mapper,
            metrics,
            executor,
            responses);
    when(dataClient.find(anyString(), anyString())).thenReturn(java.util.Optional.empty());

    // When
    soloAlterno.process(input());

    // Then
    assertThat(mensajes())
        .anyMatch(
            m ->
                m.startsWith("[EMS-ALTERNO] Resultado: Error send offer to EMS alterno")
                    && m.contains("Falló en Consulta a la base de datos"));
  }

  @Test
  void sinDestinosActivosNoEstaActivo() {
    // Given
    OfferFulfillmentEmsSender sinDestinos =
        new OfferFulfillmentEmsSender(
            props,
            new OfferKeyBuilder(props),
            dataClient,
            EventDestinations.of(),
            mapper,
            metrics,
            executor,
            responses);

    // When / Then
    assertThat(sinDestinos.isEnabled()).isFalse();
  }
}
