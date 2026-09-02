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
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import su26sd09.su26sd09.repository.NhanVienRepo;
import su26sd09.su26sd09.service.CustomerUserDetailsService;
import su26sd09.su26sd09.service.EmployeeUserDetailsService;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    CustomerUserDetailsService customerDetailsService;

    @Autowired
    EmployeeUserDetailsService employeeDetailsService;

    @Autowired
    NhanVienRepo nhanVienRepo;

    @Bean
    @Order(1)
    public SecurityFilterChain employeeSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/nhan-su/**")
                // Luu SecurityContext duoi 1 session attribute key RIENG cho khu vuc
                // nhan vien, khac voi key mac dinh ma customerSecurityFilterChain dung.
                // Ca 2 khu vuc van dung chung 1 HttpSession/JSESSIONID (khong can doi
                // cookie), nhung vi doc/ghi 2 attribute khac nhau nen dang nhap ben
                // nay se KHONG duoc cong nhan la da dang nhap ben khach (va nguoc lai).
                .securityContext(context -> context.securityContextRepository(employeeSecurityContextRepository()))
                .authenticationProvider(employeeAuthenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/nhan-su/login").permitAll()
                        .requestMatchers("/api/auth/**", "/verify-email",
                                "/*.css", "/*.js", "/*.jpg", "/*.png", "/Register", "/nhan-su/dat-phong", "/nhan-su/dat-phong/**",
                                "/nhan-su/dat-phong-quay/**", "/nhan-su/hoan-tien", "/nhan-su/hoan-tien/**", "/nhan-su/ve-sinh", "/nhan-su/ve-sinh/**")
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
                                boolean laNhanVienVeSinh = nhanVienRepo.findByEmail(authentication.getName())
                                        .map(nv -> laBoPhanVeSinh(nv.getBoPhan()))
                                        .orElse(false);
                                if (laNhanVienVeSinh) {
                                    response.sendRedirect("/nhan-su/ve-sinh");
                                } else {
                                    response.sendRedirect("/nhan-su/dat-phong");
                                }
                            }

                        }))
                        .failureHandler(loginFailureHandler("/nhan-su/login"))
                        .permitAll()
                ).sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).invalidSessionUrl("/nhan-su/login"));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityContext(context -> context.securityContextRepository(customerSecurityContextRepository()))
                .authenticationProvider(customerAuthenticationProvider())
//                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/login", "/api/auth/**", "/verify-email", "/home/**",
                                        "/loai-phong", "/loai-phong/**", "/API/payment/vnpay-payment",
                                        "/phong/**", "/phong", "/gio-hang/**", "/thanh-toan/**",
                                        "/static/**", "/css/**", "/js/**", "/images/**",
                                        "/*.css", "/*.js", "/*.jpg", "/*.png", "/Register","/khuyen-mai", "/media", "/test", "/media/**", "/tra-cuu-don").permitAll()
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
                        .failureHandler(loginFailureHandler("/login"))
                        .permitAll()
                ).sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).invalidSessionUrl("/login"));

        return http.build();
    }

    /**
     * Tao AuthenticationFailureHandler dung chung cho 1 trang login cu the:
     * phan biet tai khoan bi VO HIEU HOA (DisabledException - nem ra tu
     * DaoAuthenticationProvider khi UserDetails.isEnabled() == false, xem
     * EmployeeUserDetailsService/CustomerUserDetailsService) voi cac loi dang
     * nhap khac (sai email/mat khau -> BadCredentialsException), de hien thi
     * thong bao khac nhau ben login.html (?error=disabled vs ?error=true).
     */
    private AuthenticationFailureHandler loginFailureHandler(String loginPage) {
        return (request, response, exception) -> {
            String errorParam = (exception instanceof DisabledException) ? "disabled" : "true";
            response.sendRedirect(loginPage + "?error=" + errorParam);
        };
    }

    /**
     * SecurityContextRepository rieng cho khu vuc /nhan-su/** - luu SecurityContext
     * duoi session attribute "NHAN_SU_SECURITY_CONTEXT" thay vi key mac dinh
     * "SPRING_SECURITY_CONTEXT" (ma customerSecurityContextRepository dung), de
     * dang nhap nhan vien khong bi cong nhan la da dang nhap ben trang khach.
     */
    @Bean
    public HttpSessionSecurityContextRepository employeeSecurityContextRepository() {
        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.setSpringSecurityContextKey("NHAN_SU_SECURITY_CONTEXT");
        return repo;
    }

    /**
     * SecurityContextRepository rieng cho khu vuc khach hang (mien trong "/nhan-su/**"),
     * dung key "KHACH_HANG_SECURITY_CONTEXT" rieng (khong dung key mac dinh nua) de
     * dam bao doc lap hoan toan voi employeeSecurityContextRepository o tren.
     */
    @Bean
    public HttpSessionSecurityContextRepository customerSecurityContextRepository() {
        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.setSpringSecurityContextKey("KHACH_HANG_SECURITY_CONTEXT");
        return repo;
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

    /**
     * Nhan dien bo phan "Ve Sinh" (housekeeping/janitor) chi de quyet dinh
     * trang dieu huong sau khi dang nhap. Duoc khai bao truc tiep tai day
     * (thay vi goi NhanVienService.laBoPhanVeSinh) de tranh circular
     * dependency: SecurityConfig -> NhanVienService -> UserService ->
     * PasswordEncoder (bean duoc khai bao trong chinh SecurityConfig).
     */
    private static boolean laBoPhanVeSinh(String boPhan) {
        if (boPhan == null) {
            return false;
        }
        String normalized = java.text.Normalizer.normalize(boPhan.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String lower = normalized.toLowerCase();
        return lower.contains("ve sinh") || lower.contains("housekeeping") || lower.contains("buong phong");
    }
}
