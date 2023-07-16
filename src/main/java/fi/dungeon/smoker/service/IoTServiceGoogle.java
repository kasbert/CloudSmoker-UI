
package fi.dungeon.smoker.service;

import org.apache.commons.lang3.ObjectUtils.Null;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.annotation.PostConstruct;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Lists;
import com.google.api.client.util.Maps;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import com.google.api.services.cloudiot.v1.model.DeviceConfig;
import com.google.api.services.cloudiot.v1.model.DeviceRegistry;
import com.google.api.services.cloudiot.v1.model.DeviceState;
import com.google.api.services.cloudiot.v1.model.ListDeviceStatesResponse;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import fi.dungeon.smoker.entity.Device;


@Service("iotServiceGoogle")
@Lazy(true)
//@ConditionalOnProperty(prefix = "app.iot", name = "impl", havingValue = "google")
public class IoTServiceGoogle implements IoTService {
	Logger logger = LoggerFactory.getLogger(IoTServiceGoogle.class);

	private static final String APP_NAME = "smoker";

        @Value("${gcs-cloud-region}")
	private String cloudRegion;

	private String registryName;
	private String projectId;
	private GoogleCredentials credential;

	@Autowired
	private GcpProjectIdProvider projectIdProvider;
	@Autowired
	private CredentialsProvider credentialsProvider;

/* 
	public IoTServiceGoogle(GcpProjectIdProvider projectIdProvider, CredentialsProvider credentialsProvider)
			throws Exception {
		this.projectId = projectIdProvider.getProjectId();
		this.credential = ((GoogleCredentials) credentialsProvider.getCredentials()).createScoped(CloudIotScopes.all());
	}
*/

	@PostConstruct
	public void init() throws GeneralSecurityException, IOException {
		this.projectId = projectIdProvider.getProjectId();
		this.credential = ((GoogleCredentials) credentialsProvider.getCredentials()).createScoped(CloudIotScopes.all());
		listRegistries(projectId, cloudRegion);
	}

	private CloudIot getService() throws GeneralSecurityException, IOException {
		JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
		HttpRequestInitializer init = new HttpCredentialsAdapter(credential);
		final CloudIot service = new CloudIot.Builder(GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, init)
				.setApplicationName(APP_NAME).build();
		return service;
	}

	protected void listRegistries(String projectId, String cloudRegion) throws GeneralSecurityException, IOException {

		final CloudIot service = getService();
		final String projectPath = "projects/" + projectId + "/locations/" + cloudRegion;

		List<DeviceRegistry> registries = service.projects().locations().registries().list(projectPath).execute()
				.getDeviceRegistries();

		if (registries != null) {
			logger.debug("Found " + registries.size() + " registries");
			for (DeviceRegistry r : registries) {
				logger.debug("Id: " + r.getId() + ", Name: " + r.getName());
				if (r.getMqttConfig() != null) {
					logger.debug("Config: " + r.getMqttConfig().toPrettyString());
					this.registryName = r.getId();
				}
			}
		} else {
			logger.warn("Project has no registries.");
		}
	}

	public List<fi.dungeon.smoker.entity.Device> listDevices()
			throws GeneralSecurityException, IOException {
		final CloudIot service = getService();

		final String registryPath = String.format("projects/%s/locations/%s/registries/%s", projectId, cloudRegion,
				registryName);

		List<com.google.api.services.cloudiot.v1.model.Device> devices = service.projects().locations().registries().devices().list(registryPath).execute()
				.getDevices();

		List<fi.dungeon.smoker.entity.Device> result = Lists.newArrayList();
		if (devices != null) {
			logger.debug("Found {} devices", devices.size());
			for (com.google.api.services.cloudiot.v1.model.Device d : devices) {
				logger.debug("Id: {}", d.getId());
				// Fetch details
				final String devicePath = String.format("projects/%s/locations/%s/registries/%s/devices/%s", projectId,
				cloudRegion, registryName, d.getId());
				logger.debug("Retrieving device {}", devicePath);
				com.google.api.services.cloudiot.v1.model.Device device = service.projects().locations().registries().devices().get(devicePath).execute();
				if (device.getGatewayConfig() != null
					&& "GATEWAY".equalsIgnoreCase(device.getGatewayConfig().getGatewayType())) {
					continue;
				}
				result.add(toDevice(device));
			}
		} else {
			logger.warn("Registry has no devices.");
		}
		return result;
	}

	private fi.dungeon.smoker.entity.Device toDevice(com.google.api.services.cloudiot.v1.model.Device device) throws IOException {
		JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
		fi.dungeon.smoker.entity.Device result = new fi.dungeon.smoker.entity.Device();
		BeanUtils.copyProperties(device, result);
		result.put("name", device.getName());
		result.put("id", device.getId());

		String configData = "{}";
		if (device.getConfig() != null && device.getConfig().getBinaryData() != null) {
			configData = new String(Base64.decodeBase64(device.getConfig().getBinaryData()));
		}
		Map<String,String> configMap = new HashMap((Map<String,String>)jsonFactory.fromString(configData, Map.class));
		configMap.put("cloudUpdateTime", device.getConfig().getCloudUpdateTime());
		configMap.put("deviceAckTime", device.getConfig().getDeviceAckTime());
		result.put("config", configMap);

		String stateData = "{}";
		if (device.getState() != null && device.getState().getBinaryData() != null) {
			stateData = new String(Base64.decodeBase64(device.getState().getBinaryData()));
		}
		Map<String,String> stateMap = new HashMap((Map<String,String>)jsonFactory.fromString(stateData, Map.class));
		stateMap.put("updateTime", device.getState().getUpdateTime());
		result.put("state", stateMap);

		logger.debug("device: {}", result);
		return result;
	}

	/** Retrieves device metadata from a registry. * */
	public fi.dungeon.smoker.entity.Device getDevice(String deviceId)
			throws GeneralSecurityException, IOException {
		final CloudIot service = getService();
		final String devicePath = String.format("projects/%s/locations/%s/registries/%s/devices/%s", projectId,
				cloudRegion, registryName, deviceId);
		logger.debug("Retrieving device {}", devicePath);
		com.google.api.services.cloudiot.v1.model.Device device = service.projects().locations().registries().devices().get(devicePath).execute();
		fi.dungeon.smoker.entity.Device result = toDevice(device);
		return result;
	}

	/** Retrieves device metadata from a registry. * */
	protected List<DeviceState> getDeviceStates(String deviceId, String projectId, String cloudRegion,
			String registryName) throws GeneralSecurityException, IOException {
		final CloudIot service = getService();
		final String devicePath = String.format("projects/%s/locations/%s/registries/%s/devices/%s", projectId,
				cloudRegion, registryName, deviceId);
		logger.debug("Retrieving device states {}", devicePath);
		ListDeviceStatesResponse resp = service.projects().locations().registries().devices().states().list(devicePath)
				.execute();
		return resp.getDeviceStates();
	}

	public void setDeviceConfiguration(String deviceId, String data, long version)
			throws GeneralSecurityException, IOException {
		final CloudIot service = getService();
		final String devicePath = String.format("projects/%s/locations/%s/registries/%s/devices/%s", this.projectId,
				this.cloudRegion, this.registryName, deviceId);
		ModifyCloudToDeviceConfigRequest req = new ModifyCloudToDeviceConfigRequest();
		req.setVersionToUpdate(version);

		// Data sent through the wire has to be base64 encoded.
		String encPayload = Base64.encodeBase64String(data.getBytes(StandardCharsets.UTF_8.name()));
		req.setBinaryData(encPayload);

		DeviceConfig config = service.projects().locations().registries().devices()
				.modifyCloudToDeviceConfig(devicePath, req).execute();

		logger.info("Updated: " + config.getVersion());
	}
}
