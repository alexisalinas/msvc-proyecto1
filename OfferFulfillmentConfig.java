package com.banamex.app.config;

import com.banamex.cops.app.service.impl.CopOfferFulfillmentServiceImpl;
import com.banamex.cops.app.service.impl.CopSuppressionServiceImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Registra los servicios de la librería {@code chnn-cop-lib-operation-db}. Los repositorios JPA los
 * habilita {@code CopsDbAutoConfiguration}, por eso aquí no se habilitan otra vez.
 */
@Configuration
@Import({CopOfferFulfillmentServiceImpl.class, CopSuppressionServiceImpl.class})
public class OfferFulfillmentConfig {}
