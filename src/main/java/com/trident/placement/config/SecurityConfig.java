package com.trident.placement.config;

import com.trident.placement.security.AzureJwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
// @EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AzureJwtAuthFilter azureJwtAuthFilter;
    private final CorsConfig corsConfig;

    // @Bean
    // public SecurityFilterChain securityFilterChain(HttpSecurity http) throws
    // Exception {
    //
    // http
    // .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
    // .csrf(csrf -> csrf.disable())
    // .sessionManagement(session ->
    // session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    // )
    // .authorizeHttpRequests(auth -> auth
    // .requestMatchers("/api/auth/**").permitAll()
    // .requestMatchers("/api/public/**").permitAll()
    // .requestMatchers("/api/get-user-role").authenticated()
    // .requestMatchers("/api/admin/**").hasRole("ADMIN")
    // .requestMatchers("/api/tpo/**").hasAnyRole("TPO", "ADMIN")
    // .requestMatchers("/api/student/dashboard/**").permitAll()
    // .requestMatchers("/api/drives/**").permitAll()
    // .requestMatchers("/api/applications/**").permitAll()
    // .requestMatchers("/api/profile/**").permitAll()
    // .requestMatchers("/api/notifications/**").permitAll()
    // .requestMatchers("/api/stats/**").permitAll()
    // .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
    // "/swagger-ui.html").permitAll()
    // .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
    //
    // .anyRequest().authenticated()
    // )
    // .httpBasic(basic -> basic.disable())
    // .formLogin(form -> form.disable())
    // .addFilterBefore(azureJwtAuthFilter,
    // UsernamePasswordAuthenticationFilter.class);
    //
    // return http.build();
    // }

    // for temporary testing only

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

}