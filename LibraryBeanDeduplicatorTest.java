package com.banamex.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

/** Pruebas unitarias de {@code LibraryBeanDeduplicator}. */
class LibraryBeanDeduplicatorTest {

  private static final String PREFIX = "com.banamex.app.config.LibraryBeanDeduplicatorTest$";

  @Repository
  public static class Repositorio {}

  public interface Servicio {}

  @Service
  public static class ServicioImpl implements Servicio {
    public ServicioImpl(Repositorio repositorio) {}
  }

  @Configuration
  @Import({Repositorio.class, ServicioImpl.class})
  static class Libreria {}

  @Configuration
  static class ConfigViejaDelMicro {

    @Bean
    Repositorio copTimezoneLookupRepository() {
      return new Repositorio();
    }

    @Bean
    Servicio copTimezoneLookupService(Repositorio repositorio) {
      return new ServicioImpl(repositorio);
    }
  }

  @Configuration
  static class DosRepositorios {

    @Bean
    Repositorio repositorioUno() {
      return new Repositorio();
    }

    @Bean
    Repositorio repositorioDos() {
      return new Repositorio();
    }
  }

  private ApplicationContextRunner runner(Class<?>... configuraciones) {
    return new ApplicationContextRunner()
        .withUserConfiguration(configuraciones)
        .withBean(LibraryBeanDeduplicator.class, () -> new LibraryBeanDeduplicator(PREFIX));
  }

  @Test
  void conUnRegistroDuplicadoElContextoArrancaAndQuedaSoloElDeLaLibreria() {
    // Given
    ApplicationContextRunner runner = runner(Libreria.class, ConfigViejaDelMicro.class);

    // When / Then
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx.getBeanNamesForType(Repositorio.class))
              .containsExactly(Repositorio.class.getName());
          assertThat(ctx.getBeanNamesForType(Servicio.class))
              .containsExactly(ServicioImpl.class.getName());
        });
  }

  @Test
  void sinDuplicadosNoQuitaNada() {
    // Given
    ApplicationContextRunner runner = runner(Libreria.class);

    // When / Then
    runner.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx.getBeanNamesForType(Repositorio.class)).hasSize(1);
          assertThat(ctx.getBeanNamesForType(Servicio.class)).hasSize(1);
        });
  }

  @Test
  void siNoHayRegistroDeLaLibreriaNoQuitaElDelMicro() {
    // Given
    ApplicationContextRunner runner = runner(ConfigViejaDelMicro.class);

    // When / Then
    runner.run(
        ctx ->
            assertThat(ctx.getBeanNamesForType(Repositorio.class))
                .containsExactly("copTimezoneLookupRepository"));
  }

  @Test
  void soloTocaLasClasesDelPaqueteIndicado() {
    // Given
    ApplicationContextRunner otroPaquete =
        new ApplicationContextRunner()
            .withUserConfiguration(DosRepositorios.class)
            .withBean(
                LibraryBeanDeduplicator.class,
                () -> new LibraryBeanDeduplicator("com.otra.libreria."));

    // When / Then
    otroPaquete.run(
        ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx.getBeanNamesForType(Repositorio.class)).hasSize(2);
        });
  }
}
