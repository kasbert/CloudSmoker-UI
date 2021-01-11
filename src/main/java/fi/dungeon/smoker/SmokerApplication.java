package fi.dungeon.smoker;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@SpringBootApplication
public class SmokerApplication extends WebSecurityConfigurerAdapter {

	Logger logger = LoggerFactory.getLogger(SmokerApplication.class);

	@Value("${allowedUsers}")
	private List<String> allowedUsers;

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		// @formatter:off
        http
            .authorizeRequests(a -> a
                .antMatchers("/", "/error", "/webjars/**", "/smoker.js", "/login*").permitAll()
                .anyRequest().authenticated()
            )
			.logout(l -> l
	            .logoutSuccessUrl("/").permitAll()
			)
	        .csrf(c -> c
    	        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
        	)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
			)
            .oauth2Login();
        // @formatter:on
	}

	@Bean
	public OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService() {
		DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
		return request -> {
			OAuth2User user = delegate.loadUser(request);
			String login = user.getAttribute("login");
			logger.info("Login {} ", user);
			//if (!"github".equals(request.getClientRegistration().getRegistrationId())) {
			//	return user;
			//}
			//OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(request.getClientRegistration(), user.getName(),
			//		request.getAccessToken());
			if (login != null  && allowedUsers.contains(login)) {
				return user;
			}
			logger.info("User is not allowed");
			throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "Not allowed", ""));
		};
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(SmokerApplication.class, args);
	}
}
