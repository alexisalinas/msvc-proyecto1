package com.banamex.app.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.banamex.app.config.OfferJmsProperties;
import com.banamex.app.testutil.TestData;
import com.banamex.app.testutil.XmlTestUtil;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;

/** Pruebas unitarias de {@code UpdateOfferRequestXmlMapper}. */
class UpdateOfferRequestXmlMapperTest {

  private UpdateOfferRequestXmlMapper mapper;

  @BeforeEach
  void setUp() {
    OfferJmsProperties props = new OfferJmsProperties();
    props.setCustomerPadding(true);
    props.setCustomerPaddingLength(10);
    UserTextParser parser = new UserTextParser(JsonMapper.builder().build());
    OfferEventMapper event = new OfferEventMapper(props, parser);
    ReflectionTestUtils.setField(event, "translateTimeZone", false);
    ReflectionTestUtils.setField(event, "ecrmTimeZone", "America/Mexico_City");
    mapper = new UpdateOfferRequestXmlMapper(event);
    ReflectionTestUtils.setField(mapper, "bodyElement", "UpdateOffer");
    ReflectionTestUtils.setField(mapper, "envelope", true);
  }

  @Test
  void emsXmlTieneLaEstructuraEnvelopeBodySinHeader() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    Document doc = XmlTestUtil.parse(xml);
    assertThat(doc.getDocumentElement().getNodeName()).isEqualTo("Envelope");
    assertThat(XmlTestUtil.childNames(doc, "Envelope")).containsExactly("Body");
    assertThat(XmlTestUtil.childNames(doc, "Body")).containsExactly("UpdateOffer");
  }

  @Test
  void emsXmlNoLlevaRqHeaderNiClientDetails() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(xml)
        .doesNotContain(
            "<Header>", "RqHeader", "ClientDetails", "<UUID>", "<ESBUUID>", "<Org>", "<UserID>");
  }

  @Test
  void emsXmlSinEnvelopeVaSoloElEvento() {
    // Given
    ReflectionTestUtils.setField(mapper, "envelope", false);

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    Document doc = XmlTestUtil.parse(xml);
    assertThat(doc.getDocumentElement().getNodeName()).isEqualTo("UpdateOffer");
    assertThat(xml).doesNotContain("Envelope", "<Body>");
    assertThat(XmlTestUtil.childNames(doc, "UpdateOffer")).isNotEmpty();
  }

  @Test
  void emsXmlConAndSinEnvelopeLlevaLosMismosCampos() {
    // Given
    String conEnvelope =
        mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);
    ReflectionTestUtils.setField(mapper, "envelope", false);

    // When
    String sinEnvelope =
        mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(XmlTestUtil.childNames(XmlTestUtil.parse(conEnvelope), "UpdateOffer"))
        .isEqualTo(XmlTestUtil.childNames(XmlTestUtil.parse(sinEnvelope), "UpdateOffer"));
  }

  @Test
  void emsXmlNoTieneUrlsNiPrefijos() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(XmlTestUtil.hasUrls(xml)).isFalse();
    assertThat(xml).doesNotContain("soapenv:", "head:", "ns0:", "ns1:", "ns:");
  }

  @Test
  void emsXmlLlevaLosCamposTransformados() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(xml)
        .contains(
            "<RequestingChannel>CAT</RequestingChannel>",
            "<CardNO>1234567812345678</CardNO>",
            "<CustomerNO>0012345678</CustomerNO>",
            "<ApptStartTime>10:30</ApptStartTime>",
            "<UserField1>valor 1</UserField1>",
            "<UserField100>valor 100</UserField100>",
            "<UserField3></UserField3>");
  }

  @Test
  void emsXmlLaHoraDeLaPeticionVaEnUpdateTime() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(xml).contains("<UpdateTime>2026-10-26T10:54:55.852</UpdateTime>");
  }

  @Test
  void emsXmlNingunElementoDelEventoSeRepite() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    Document doc =
        XmlTestUtil.parse(
            mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null));

    // Then
    List<String> hijos = XmlTestUtil.childNames(doc, "UpdateOffer");
    assertThat(hijos).isNotEmpty().doesNotHaveDuplicates();
  }

  @Test
  void emsXmlEscapaLosCaracteresEspeciales() {
    // Given
    var rq = TestData.request();
    rq.setComments("a & b < c");

    // When
    String xml = mapper.toEmsXml(rq, TestData.requestTime(), null, null, null);

    // Then
    assertThat(xml).contains("<Comments>a &amp; b &lt; c</Comments>");
    XmlTestUtil.parse(xml);
  }

  @Test
  void emsXmlUsaElNombreDeElementoConfigurable() {
    // Given
    ReflectionTestUtils.setField(mapper, "bodyElement", "OtroEvento");

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(xml).contains("<OtroEvento>", "</OtroEvento>");
  }

  @Test
  void emsXmlUpdatedByLlevaElClientIdQueSePasa() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    String xml =
        mapper.toEmsXml(TestData.request(), TestData.requestTime(), "client-123", null, null);

    // Then
    assertThat(xml).contains("<UpdatedBy>client-123</UpdatedBy>");
  }

  @Test
  void emsPropertiesEmpiezanConNmAndNsAndLlevanLosCamposDelEvento() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    var props =
        mapper.toEmsProperties(
            TestData.request(), TestData.requestTime(), "client-123", null, null);

    // Then
    assertThat(props.keySet()).startsWith("_nm_", "_ns_");
    assertThat(props.get("_nm_")).isEqualTo("UpdateOffer");
    assertThat(props.get("_ns_"))
        .isEqualTo("www.tibco.com/be/ontology/OfferEngine/Events/UpdateOffer");
    assertThat(props.get("CampaignID")).isEqualTo("CAMP001");
    assertThat(props.get("CustomerNO")).isEqualTo("0012345678");
    assertThat(props.get("UpdatedBy")).isEqualTo("client-123");
    assertThat(props.get("UserField1")).isEqualTo("valor 1");
  }

  @Test
  void emsPropertiesTienenLosMismosCamposQueElXml() {
    // Given
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // When
    var props =
        mapper.toEmsProperties(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    List<String> camposDelXml = XmlTestUtil.childNames(XmlTestUtil.parse(xml), "UpdateOffer");
    List<String> camposDeLasPropiedades =
        props.keySet().stream().filter(k -> !k.startsWith("_")).toList();
    assertThat(camposDeLasPropiedades).containsExactlyElementsOf(camposDelXml);
  }

  @Test
  void emsPropertiesElNombreAndElNamespaceDelEventoSonConfigurables() {
    // Given
    ReflectionTestUtils.setField(mapper, "bodyElement", "OtroEvento");
    ReflectionTestUtils.setField(mapper, "eventNamespace", "otro.namespace");

    // When
    var props =
        mapper.toEmsProperties(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(props.get("_nm_")).isEqualTo("OtroEvento");
    assertThat(props.get("_ns_")).isEqualTo("otro.namespace");
  }

  @Test
  void emsPropertiesElElementoNoEscribeEnvelopeNiNamespaceEnElXml() {
    // Given
    // (sin precondiciones propias: se usan los datos de setUp)

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(xml).doesNotContain("www.tibco.com", "_ns_", "_nm_");
  }

  @Test
  void emsXmlPorDefectoElElementoDelEventoVaSinNamespace() {
    // Given
    ReflectionTestUtils.setField(mapper, "envelope", false);

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(xml).startsWith("<UpdateOffer>").doesNotContain("xmlns");
  }

  @Test
  void emsXmlConXmlNamespaceElElementoDelEventoLlevaSuNamespaceSinPrefijo() {
    // Given
    ReflectionTestUtils.setField(mapper, "envelope", false);
    ReflectionTestUtils.setField(mapper, "xmlNamespace", true);

    // When
    String xml = mapper.toEmsXml(TestData.request(), TestData.requestTime(), null, null, null);

    // Then
    assertThat(xml)
        .startsWith(
            "<UpdateOffer xmlns=\"www.tibco.com/be/ontology/OfferEngine/Events/UpdateOffer\">");
    XmlTestUtil.parse(xml);
  }
}
