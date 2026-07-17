package su26sd09.su26sd09.config;

import jakarta.servlet.SessionCookieConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.reactive.PathRequest;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.CachingUserDetailsService;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import su26sd09.su26sd09.service.CustomerUserDetailsService;
import su26sd09.su26sd09.service.EmployeeUserDetailsService;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    CustomerUserDetailsService customerDetailsService;

    @Autowired
    EmployeeUserDetailsService employeeDetailsService;

    @Bean
    @Order(1)
    public SecurityFilterChain employeeSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/nhan-su/**")
                .authenticationProvider(employeeAuthenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/nhan-su/login").permitAll()
                        .requestMatchers("/api/auth/**", "/verify-email",
                                "/*.css", "/*.js", "/*.jpg", "/*.png", "/Register", "/nhan-su/dat-phong", "/nhan-su/dat-phong/**",
                                "/nhan-su/dat-phong-quay/**", "/nhan-su/hoan-tien", "/nhan-su/hoan-tien/**")
                        .hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/nhan-su/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/nhan-su/login")
                        .loginProcessingUrl("/nhan-su/login")
                        .successHandler(((request, response, authentication) ->
                        {
                            if (authentication.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                                response.sendRedirect("/nhan-su/admin/thong-ke");
                            } else if (authentication.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"))) {
                                response.sendRedirect("/nhan-su/dat-phong");
                            }

                        }))
                        .failureUrl("/nhan-su/login?error=true")
                        .permitAll()
                ).sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).invalidSessionUrl("/nhan-su/login"));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authenticationProvider(customerAuthenticationProvider())
//                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/login", "/api/auth/**", "/verify-email", "/home/**",
                                        "/loai-phong", "/loai-phong/**", "/API/payment/vnpay-payment",
                                        "/phong/**", "/phong", "/gio-hang/**", "/thanh-toan/**",
                                        "/static/**", "/css/**", "/js/**", "/images/**",
                                        "/*.css", "/*.js", "/*.jpg", "/*.png", "/Register","/khuyen-mai").permitAll()
//                                .requestMatchers("/admin/dat-phong-quay/**")
//                                .hasAnyRole("STAFF","ADMIN").requestMatchers("/Nhan-vien/**").hasRole("STAFF")
//                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                .anyRequest().authenticated()
//                                .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/home", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                ).sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).invalidSessionUrl("/login"));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider customerAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customerDetailsService);

        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationProvider employeeAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(employeeDetailsService);

        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
