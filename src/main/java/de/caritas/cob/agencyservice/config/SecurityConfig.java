package de.caritas.cob.agencyservice.config;

import de.caritas.cob.agencyservice.api.authorization.Authority.AuthorityValue;
import de.caritas.cob.agencyservice.config.security.AuthorisationService;
import de.caritas.cob.agencyservice.config.security.JwtAuthConverter;
import de.caritas.cob.agencyservice.config.security.JwtAuthConverterProperties;
import de.caritas.cob.agencyservice.filter.HttpTenantFilter;
import de.caritas.cob.agencyservice.filter.StatelessCsrfFilter;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Provides the Keycloak/Spring Security configuration.
 */
@KeycloakConfiguration
@EnableGlobalMethodSecurity(
    prePostEnabled = true)
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig implements WebMvcConfigurer {

  public static final String[] WHITE_LIST =
      new String[]{"/agencies/docs", "/agencies/docs/**", "/v2/api-docs", "/configuration/ui",
          "/swagger-resources/**", "/configuration/security", "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/actuator/health", "/actuator/health/**"};

  @Autowired
  AuthorisationService authorisationService;
  @Autowired
  JwtAuthConverterProperties jwtAuthConverterProperties;


  @Value("${csrf.cookie.property}")
  private String csrfCookieProperty;

  @Value("${csrf.header.property}")
  private String csrfHeaderProperty;

  @Autowired
  private Environment environment;

  @Autowired(required = false)
  @Nullable
  private HttpTenantFilter httpTenantFilter;

  @Value("${multitenancy.enabled}")
  private boolean multitenancy;

  /**
   * Configure spring security filter chain: disable default Spring Boot CSRF token behavior and add
   * custom {@link StatelessCsrfFilter}, set all sessions to be fully stateless, define necessary
   * Keycloak roles for specific REST API paths
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    var httpSecurity = http.csrf().disable()
        .addFilterBefore(new StatelessCsrfFilter(csrfCookieProperty, csrfHeaderProperty),
            CsrfFilter.class);

    if (multitenancy) {
      httpSecurity = httpSecurity
          .addFilterAfter(httpTenantFilter, BearerTokenAuthenticationFilter.class);
    }

    httpSecurity.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and().authorizeRequests()
        .requestMatchers(WHITE_LIST).permitAll()
        .requestMatchers("/agencies").permitAll()
        .requestMatchers(HttpMethod.GET, "/agencyadmin/agencies")
        .hasAuthority(AuthorityValue.SEARCH_AGENCIES)
        .requestMatchers("/agencies/by-tenant").hasAuthority(AuthorityValue.SEARCH_AGENCIES_WITHIN_TENANT)
        .requestMatchers("/agencyadmin/agencies/tenant/*")
        .access("hasAuthority('" + AuthorityValue.AGENCY_ADMIN
            + "') and hasAuthority('" + AuthorityValue.TENANT_ADMIN + "')")
        .requestMatchers("/agencyadmin", "/agencyadmin/", "/agencyadmin/**")
        .hasAnyAuthority(AuthorityValue.AGENCY_ADMIN, AuthorityValue.RESTRICTED_AGENCY_ADMIN)
        .requestMatchers("/agencies/**").permitAll()
        .anyRequest().denyAll();


    httpSecurity.oauth2ResourceServer().jwt().jwtAuthenticationConverter(jwtAuthConverter());
    return httpSecurity.build();
  }

  /**
   * Configure trailing slash match for all endpoints (needed as Spring Boot 3.0.0 changed default behaviour for trailing slash match)
   * https://www.baeldung.com/spring-boot-3-migration (section 3.1)
   */
  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.setUseTrailingSlashMatch(true);
  }

  @Bean
  public JwtAuthConverter jwtAuthConverter() {
    return new JwtAuthConverter(jwtAuthConverterProperties, authorisationService);
  }



}
