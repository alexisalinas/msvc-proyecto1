package com.banamex.app.service;

import com.banamex.app.config.UpdateOfferResponseProperties;
import com.banamex.app.dto.UpdateOfferRequest;
import com.banamex.app.dto.UpdateOfferResponse;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Arma las respuestas que el micro devuelve por cada oferta y los textos de resultado del envío del
 * evento. Los códigos y los textos salen de {@link UpdateOfferResponseProperties}; la fecha se
 * escribe en la zona ECRM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateOfferResponseFactory {

  /** Códigos, textos y formato de fecha configurados. */
  private final UpdateOfferResponseProperties props;

  /** Zona en la que se escribe la fecha de los textos. */
  @Value("${update-offer.timezone.ecrm-time-zone:US/Central}")
  private String ecrmTimeZone = "US/Central";

  /** Reloj usado para la fecha; se reemplaza en las pruebas. */
  private Clock clock = Clock.systemUTC();

  /**
   * Respuesta de éxito genérica.
   *
   * @return código y texto de éxito configurados
   */
  public UpdateOfferResponse success() {
    return build(props.getSuccessCode(), props.getSuccessText(), "", "");
  }

  /**
   * Respuesta de éxito de una oferta. Si la oferta tiene estado {@code U} y ese formato está
   * activo, responde con la llave de la oferta; si no trae {@code campaignId}, responde con el
   * código de campaña faltante. En cualquier otro caso, el éxito genérico.
   *
   * @param request oferta procesada
   * @return respuesta de éxito
   */
  public UpdateOfferResponse success(UpdateOfferRequest request) {
    if (request == null
        || !props.isUpdatedStatusBwFormat()
        || !"U".equals(request.getOfferStatus())) {
      return success();
    }
    UpdateOfferResponse r = new UpdateOfferResponse();
    if (isEmpty(request.getCampaignId())) {
      r.setCode(props.getUpdatedStatusCampaignMissingCode());
      r.setDescription(
          props
              .getUpdatedStatusCampaignMissingText()
              .replace(
                  "{campaignId}", request.getCampaignId() == null ? "" : request.getCampaignId()));
    } else {
      r.setCode(props.getUpdatedStatusSuccessCode());
      r.setDescription(
          props.getUpdatedStatusSuccessText().replace("{offerKey}", updateKey(request)));
    }
    return r;
  }

  /**
   * Respuesta de éxito teniendo en cuenta lo que contestó el EMS. Con {@code
   * update-offer.response.source=ems} y una respuesta del EMS con código, devuelve la del EMS
   * (salvo en el estado {@code U}); en los demás casos arma la respuesta del micro.
   *
   * @param request oferta procesada
   * @param emsResponse respuesta leída del EMS, o {@code null}
   * @return respuesta de la oferta
   */
  public UpdateOfferResponse success(UpdateOfferRequest request, UpdateOfferResponse emsResponse) {
    boolean esU =
        request != null && props.isUpdatedStatusBwFormat() && "U".equals(request.getOfferStatus());
    log.info(
        "[RESPONSE-SOURCE] source={} statusU={} emsCode={}",
        props.getSource(),
        esU,
        emsResponse == null ? null : emsResponse.getCode());
    if (!esU
        && "ems".equalsIgnoreCase(props.getSource())
        && emsResponse != null
        && emsResponse.getCode() != null
        && !emsResponse.getCode().isBlank()) {
      return emsResponse;
    }
    return success(request);
  }

  /**
   * Llave de la oferta para el estado {@code U}: {@code
   * off:mx:campaña_ola_cliente[_cuenta][_tarjeta]}.
   *
   * @param rq oferta
   * @return llave de la oferta
   */
  static String updateKey(UpdateOfferRequest rq) {
    StringBuilder key =
        new StringBuilder("off:mx:")
            .append(nz(rq.getCampaignId()))
            .append('_')
            .append(nz(rq.getWaveId()))
            .append('_')
            .append(nz(rq.getCustomerId()));
    if (!isEmpty(rq.getAccountNumber())) {
      key.append('_').append(rq.getAccountNumber());
    }
    if (!isEmpty(rq.getCardNumber())) {
      key.append('_').append(rq.getCardNumber());
    }
    return key.toString();
  }

  /**
   * Un texto es vacío si es nulo o no tiene caracteres; uno con solo espacios cuenta como con
   * valor.
   *
   * @param s texto
   * @return {@code true} si es nulo o vacío
   */
  private static boolean isEmpty(String s) {
    return s == null || s.isEmpty();
  }

  /**
   * Devuelve el texto o una cadena vacía si es nulo.
   *
   * @param s texto
   * @return el texto, o cadena vacía si es nulo
   */
  private static String nz(String s) {
    return s == null ? "" : s;
  }

  /**
   * Respuesta de error de una oferta.
   *
   * @param step nombre del paso que falló
   * @param cause causa de la falla
   * @return código de error y texto con el paso y el detalle
   */
  public UpdateOfferResponse error(String step, Throwable cause) {
    return build(props.getErrorCode(), props.getErrorText(), step, detail(cause));
  }

  /**
   * Texto de resultado cuando el evento se publicó.
   *
   * @param destination nombre del destino
   * @return texto con la fecha
   */
  public String kafkaSuccessText(String destination) {
    return format(props.getKafkaSuccessText(), "", "").replace("{destination}", nz(destination));
  }

  /**
   * Texto de resultado cuando el evento no tiene ningún destino activo.
   *
   * @return texto con la fecha
   */
  public String kafkaDisabledText() {
    return format(props.getKafkaDisabledText(), "", "");
  }

  /**
   * Texto de resultado cuando el destino está en dry-run y no se publicó nada.
   *
   * @param destination nombre del destino
   * @return texto con la fecha
   */
  public String kafkaDryRunText(String destination) {
    return format(props.getKafkaDryRunText(), "", "").replace("{destination}", nz(destination));
  }

  /**
   * Texto de resultado cuando el envío al destino falló.
   *
   * @param destination nombre del destino
   * @param step paso que falló
   * @param detail detalle de la falla
   * @return texto con la fecha
   */
  public String kafkaErrorText(String destination, String step, String detail) {
    return format(props.getKafkaErrorText(), step, detail)
        .replace("{destination}", nz(destination));
  }

  /**
   * Igual que el anterior, con el detalle tomado de la causa más profunda de la excepción.
   *
   * @param destination nombre del destino
   * @param step paso que falló
   * @param cause excepción
   * @return texto con la fecha
   */
  public String kafkaErrorText(String destination, String step, Throwable cause) {
    return kafkaErrorText(destination, step, detail(cause));
  }

  /**
   * Crea la respuesta con el código y el texto ya completado.
   *
   * @param code código
   * @param textTemplate texto con marcadores
   * @param step paso para el marcador {@code {step}}
   * @param detail detalle para el marcador {@code {detail}}
   * @return respuesta
   */
  private UpdateOfferResponse build(String code, String textTemplate, String step, String detail) {
    UpdateOfferResponse r = new UpdateOfferResponse();
    r.setCode(code);
    r.setDescription(format(textTemplate, step, detail));
    return r;
  }

  /**
   * Reemplaza los marcadores {@code {dateTime}}, {@code {step}} y {@code {detail}} del texto.
   *
   * @param template texto con marcadores
   * @param step paso
   * @param detail detalle
   * @return texto completo
   */
  private String format(String template, String step, String detail) {
    return template
        .replace("{dateTime}", dateTime())
        .replace("{step}", step == null ? "" : step)
        .replace("{detail}", detail == null ? "" : detail);
  }

  /**
   * Fecha y hora actuales en la zona ECRM, con el formato configurado.
   *
   * @return fecha y hora en texto
   */
  String dateTime() {
    return ZonedDateTime.now(clock.withZone(ZoneId.of(ecrmTimeZone)))
        .format(DateTimeFormatter.ofPattern(props.getDateFormat()));
  }

  /**
   * Mensaje de la causa más profunda; si no tiene, el nombre de su clase.
   *
   * @param cause excepción
   * @return detalle de la falla
   */
  private static String detail(Throwable cause) {
    if (cause == null) {
      return "";
    }
    Throwable root = cause;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
  }
}
