package com.cityledger.cityledger.config;

import com.cityledger.cityledger.service.CustomOAuth2UserService;
import com.cityledger.cityledger.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomUserDetailsService customUserDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public pages — no login needed
                .requestMatchers("/", "/login", "/signup", "/how-it-works", "/ai-features", "/api/ai/demo", "/css/**", "/js/**", "/images/**", "/*.png", "/webjars/**").permitAll()
                // OAuth2 flow must be publicly accessible
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // Role-specific dashboard routes
                .requestMatchers("/officer/**").hasAnyRole("OFFICER", "ADMIN")
                .requestMatchers("/api/**").hasAnyRole("OFFICER", "ADMIN", "CITIZEN", "FIELD_WORKER")
                .requestMatchers("/field-worker/**").hasAnyRole("FIELD_WORKER", "ADMIN")
                .requestMatchers("/citizen/**").hasAnyRole("CITIZEN", "ADMIN", "OFFICER", "FIELD_WORKER")
                // Any other request needs to be authenticated
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .userInfoEndpoint(info -> info
                    .userService(customOAuth2UserService)
                )
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error=true")
            )
            .userDetailsService(customUserDetailsService)
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }
}
