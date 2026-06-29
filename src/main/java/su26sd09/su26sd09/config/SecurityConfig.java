package su26sd09.su26sd09.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.reactive.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    CustomerUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authenticationProvider(authenticationProvider())
//                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/Login", "/api/auth/**","/verify-email","/home/**",
                                "/loai-phong", "/loai-phong/**","/API/payment/vnpay-payment",
                                "/phong/**", "/phong","/gio-hang/**","/thanh-toan/**",
                                "/static/**", "/css/**", "/js/**", "/images/**",
                                "/*.css", "/*.js", "/*.jpg", "/*.png","/Register").permitAll().requestMatchers("/admin/dat-phong-quay/**")
                                .hasAnyRole("STAFF","ADMIN").requestMatchers("/Nhan-vien/**").hasRole("STAFF")
                                .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
//                                .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/Login").
                        loginProcessingUrl("/login")
                        .defaultSuccessUrl("/home", true)
                        .failureUrl("/Login?error=true")
                        .permitAll()
                ).sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).invalidSessionUrl("/Login"));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
