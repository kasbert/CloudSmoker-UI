package fi.dungeon.smoker.service;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

import fi.dungeon.smoker.service.IoTService;
import fi.dungeon.smoker.service.IoTServiceLocal;
import fi.dungeon.smoker.service.IoTServiceGoogle;

import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.gax.core.CredentialsProvider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gcp.core.GcpProjectIdProvider;

@Configuration
public class IotServiceConfiguration {

	/*
	@Bean
	@Lazy(true)
	public GcpProjectIdProvider gcpProjectIdProvider() throws Exception {
		return new GcpProjectIdProvider();
	}

	@Bean
	@Lazy(true)
	public CredentialsProvider credentialsProvider() throws Exception {
		return new CredentialsProvider();
	}
	*/

	/*
	@Bean("iotService2")
	@Scope("singleton")
	@Lazy(true)
	@ConditionalOnProperty(prefix = "app.iot", name = "impl", havingValue = "google")
	public IoTService ioTServiceGoogle(GcpProjectIdProvider projectIdProvider, CredentialsProvider credentialsProvider) throws Exception {
		return new IoTServiceGoogle(projectIdProvider, credentialsProvider);
	}

	@Bean("iotService3")
	@Scope("singleton")
	@Lazy(true)
	@ConditionalOnProperty(prefix = "app.iot", name = "impl", havingValue = "local", matchIfMissing = true)
	public IoTService ioTServiceLocal() throws Exception {
		return new IoTServiceLocal();
	}
	*/

	@Autowired
	private ApplicationContext context;

	@Bean
	public IoTService iotService(@Value("${app.iot.impl}") String qualifier) {
	    return (IoTService) context.getBean(qualifier, IoTService.class);
	}

}
