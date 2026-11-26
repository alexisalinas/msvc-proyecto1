package com.banamex.cops.app.service.impl;

import com.banamex.cops.app.model.CopOfferFulfillment;
import com.banamex.cops.app.repository.CopFulfillmentSuppressionRepository;
import com.banamex.cops.app.service.CopOfferFulfillmentService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Implementation of {@link CopOfferFulfillmentService}: builds the model from the columns by
 * position.
 */
@Service
@RequiredArgsConstructor
public class CopOfferFulfillmentServiceImpl implements CopOfferFulfillmentService {

  static final int MAX_ROWS = 100;
  private final CopFulfillmentSuppressionRepository repository;

  /** {@inheritDoc} */
  @Override
  public List<CopOfferFulfillment> findByWaveKeyAndOfferKey(String waveKey, String offerKey) {
    return repository.findOfferFulfillment(waveKey, offerKey, PageRequest.of(0, MAX_ROWS)).stream()
        .map(
            r ->
                new CopOfferFulfillment(
                    text(r[0]),
                    text(r[1]),
                    text(r[2]),
                    text(r[3]),
                    text(r[4]),
                    text(r[5]),
                    Arrays.stream(r, 6, 106).map(CopOfferFulfillmentServiceImpl::text).toList(),
                    date(r[106]),
                    date(r[107]),
                    date(r[108]),
                    date(r[109]),
                    text(r[110]),
                    text(r[111]),
                    text(r[112])))
        .toList();
  }

  private static String text(Object value) {
    return Objects.toString(value, null);
  }

  /** Converts the date of a native query row, whatever type the driver or Hibernate returns. */
  private static Timestamp date(Object value) {
    if (value instanceof Timestamp t) {
      return t;
    }
    if (value instanceof LocalDateTime l) {
      return Timestamp.valueOf(l);
    }
    if (value instanceof OffsetDateTime o) {
      return Timestamp.from(o.toInstant());
    }
    if (value instanceof java.util.Date d) {
      return new Timestamp(d.getTime());
    }
    return null;
  }
}
