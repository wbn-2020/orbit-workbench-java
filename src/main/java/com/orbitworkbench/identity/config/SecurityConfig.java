package com.orbitworkbench.identity.config;

import com.orbitworkbench.identity.application.IdentityService;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.identity.domain.AppUserRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(IdentityService identityService) {
        return username -> {
            AppUserRecord user = identityService.findByUsername(username);
            if (user == null) {
                throw new org.springframework.security.core.userdetails.UsernameNotFoundException(username);
            }
            return new OrbitUserDetails(user);
        };
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                     PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        csrfRepository.setHeaderName("X-XSRF-TOKEN");
        csrfRepository.setParameterName("_csrf");
        return csrfRepository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                             SecurityContextRepository securityContextRepository,
                                             CsrfTokenRepository csrfRepository,
                                             SecurityProblemResponseWriter problemWriter) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler))
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/setup/status", "/api/v1/auth/csrf",
                                "/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/setup", "/api/v1/auth/login")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                problemWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHENTICATED", "请先登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                problemWriter.write(request, response, HttpStatus.FORBIDDEN,
                                        "ACCESS_DENIED", "没有权限执行此操作")))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .logout(logout -> logout.disable());
        return http.build();
    }
}
