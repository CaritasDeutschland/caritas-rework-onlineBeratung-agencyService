package de.caritas.cob.agencyservice.filter;

import static de.caritas.cob.agencyservice.api.helper.HttpUrlUtils.removeHttpPrefix;
import static java.util.Optional.empty;
import static java.util.Optional.of;

import com.google.common.base.Splitter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Setter
@Slf4j
public class NonInternetDomainSubdomainExtractor {

  @Value("${app.base.url}")
  private String applicationBaseUrl;

  public Optional<String> resolveSubdomain(String domain, String originHeaderValue) throws URISyntaxException {
    if (isApplicationBaseUrl(domain)) {
      return of(resolveSubdomainFromApplicationBaseUrl(domain));
    } else if (hasOriginHeader(originHeaderValue)) {
      return resolveSubdomain(removeHttpPrefix(originHeaderValue));
    }
    return empty();
  }

  private Optional<String> resolveSubdomain(String domain) throws URISyntaxException {
    return resolveSubdomain(domain, null);
  }

  private String resolveDomain(String applicationBaseUrl) throws URISyntaxException {
    URI uri = new URI(applicationBaseUrl);
    return uri.getHost();
  }

  private boolean isApplicationBaseUrl(String site) throws URISyntaxException {
    return site.contains(resolveDomain(applicationBaseUrl));
  }

  private boolean hasOriginHeader(String originHeaderValue) {
    return originHeaderValue != null;
  }

  private String resolveSubdomainFromApplicationBaseUrl(String domain) {
    return Splitter.on(".").split(domain).iterator().next();
  }
}
