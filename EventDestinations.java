package com.banamex.app.kafka;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Destinos activos del evento de cumplimiento: el puente a Kafka y el EMS alterno, cada uno con su
 * interruptor en el yml. La lista se arma en cada envío, así un cambio de configuración se respeta
 * sin reiniciar.
 */
@Component
public class EventDestinations {

  /**
   * Un destino del evento.
   *
   * @param tag etiqueta que se escribe en el log
   * @param name nombre que se usa en los textos de resultado
   * @param publisher quien publica en ese destino
   */
  public record EventDestination(String tag, String name, OfferPublisher publisher) {}

  /** Publicador del modo {@code dry-run}: no envía nada. */
  private static final OfferPublisher DRY_RUN = (refId, json) -> "dry-run";

  /** Origen de la lista de destinos. */
  private Supplier<List<EventDestination>> destinations;

  /**
   * Crea los destinos a partir de la configuración.
   *
   * @param kafka configuración del puente a Kafka
   * @param alternate configuración del EMS alterno
   * @param beans fábrica para obtener las plantillas JMS
   */
  @Autowired
  public EventDestinations(OfferKafkaProps kafka, AlternateEmsProps alternate, BeanFactory beans) {
    this.destinations =
        () -> {
          List<EventDestination> out = new ArrayList<>();
          boolean dryRun = "dry-run".equals(kafka.getMode());
          if (kafka.isBridgeEnabled()) {
            out.add(
                new EventDestination(
                    "KAFKA",
                    "Kafka",
                    dryRun
                        ? DRY_RUN
                        : new JmsOfferPublisher(
                            template(beans, "updateOfferJmsTemplate"),
                            kafka.getQueue(),
                            kafka.getMessageFormat(),
                            kafka.getTimeToLiveMs())));
          }
          if (alternate.isEnabled()) {
            out.add(
                new EventDestination(
                    "EMS-ALTERNO",
                    "EMS alterno",
                    dryRun
                        ? DRY_RUN
                        : new JmsOfferPublisher(
                            template(beans, alternate.getJmsTemplateName()), alternate.getQueue(),
                            alternate.getMessageFormat(), alternate.getTimeToLiveMs())));
          }
          return out;
        };
  }

  /**
   * Crea destinos fijos (para pruebas).
   *
   * @param fixed destinos
   * @return instancia con esos destinos
   */
  public static EventDestinations of(EventDestination... fixed) {
    EventDestinations result = new EventDestinations(null, null, null);
    result.destinations = () -> List.of(fixed);
    return result;
  }

  /**
   * Destinos activos, en orden: puente a Kafka y luego EMS alterno.
   *
   * @return lista de destinos, vacía si no hay ninguno
   */
  public List<EventDestination> enabled() {
    return destinations.get();
  }

  /**
   * Obtiene la plantilla JMS por nombre en el momento de publicar, así la conexión solo se crea si
   * el destino se usa.
   *
   * @param beans fábrica de beans
   * @param name nombre del bean
   * @return proveedor de la plantilla
   */
  private static Supplier<JmsTemplate> template(BeanFactory beans, String name) {
    return () -> {
      try {
        return beans.getBean(name, JmsTemplate.class);
      } catch (NoSuchBeanDefinitionException e) {
        throw new IllegalStateException(
            "no existe el bean JmsTemplate '"
                + name
                + "': revisa su conexión en digital-integration.messaging.jms (jmsTemplateName) o"
                + " en JmsConfig",
            e);
      }
    };
  }
}
