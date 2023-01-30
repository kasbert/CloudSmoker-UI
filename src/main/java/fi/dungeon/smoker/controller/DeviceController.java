package fi.dungeon.smoker.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Lists;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.services.cloudiot.v1.CloudIot;
import com.google.api.services.cloudiot.v1.CloudIotScopes;
import com.google.api.services.cloudiot.v1.model.Device;
import com.google.api.services.cloudiot.v1.model.DeviceConfig;
import com.google.api.services.cloudiot.v1.model.DeviceRegistry;
import com.google.api.services.cloudiot.v1.model.DeviceState;
import com.google.api.services.cloudiot.v1.model.ListDeviceStatesResponse;
import com.google.api.services.cloudiot.v1.model.ModifyCloudToDeviceConfigRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

@RestController
public class DeviceController {

	Logger logger = LoggerFactory.getLogger(DeviceController.class);

	private static final String APP_NAME = "smoker";

	@Value("${gcs-cloud-region}")
	private String cloudRegion;

	@Value("${influx.url}")
	private String serverURL = "http://localhost:8086";
	@Value("${influx.username}")
	private String username = "admin";
	@Value("${influx.password}")
	private String password;
	@Value("${influx.database}")
	private String databaseName = "iot";

	private String registryName;
	private String projectId;
	private GoogleCredentials credential;

	public DeviceController(GcpProjectIdProvider projectIdProvider, CredentialsProvider credentialsProvider)
			throws Exception {
		this.projectId = projectIdProvider.getProjectId();
		this.credential = ((GoogleCredentials) credentialsProvider.getCredentials()).createScoped(CloudIotScopes.all());
	}

	@PostConstruct
	public void init() throws GeneralSecurityException, IOException {
		listRegistries(projectId, cloudRegion);
	}

	@RequestMapping("/device")
	public List<Device> index() throws Exception {
		List<Device> devices = listDevices(projectId, cloudRegion, registryName);
		return devices;
	}

	@RequestMapping(value = "/device/{id}", method = RequestMethod.GET)
	public Device getDevice(@PathVariable("id") String id) throws Exception {
		Device device = getDevice(id, projectId, cloudRegion, registryName);
		device.setGatewayConfig(null);
		return device;
	}

	@RequestMapping(value = "/device/{id}", method = RequestMethod.PUT)
	public void modifyDeviceConfig(@PathVariable("id") String id, @RequestBody Map<String, String> config)
			throws Exception {
		Device device = getDevice(id, projectId, cloudRegion, registryName);
		logger.info("SET Config orig: " + device.get("config2"));
		// FIXME validate input properly
		if (!config.containsKey("mode")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing mode parameter");
		}
		if ("-2".equals(config.get("mode"))) {
			if (!config.containsKey("min") || !config.containsKey("max")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing parameters");
			}
		} else if ("-1".equals(config.get("mode"))) {
			if (!config.containsKey("onMins") || !config.containsKey("offMins")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing parameters");
			}
		} else if ("0".equals(config.get("mode"))) {
		} else if ("100".equals(config.get("mode"))) {
		} else {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Illegal parameters");
		}

		JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
		// FIXME only allowed parameters!
		String json = jsonFactory.toString(config);
		logger.info("SET Config new: " + json);
		setDeviceConfiguration(id, json, 0);
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

	protected List<Device> listDevices(String projectId, String cloudRegion, String registryName)
			throws GeneralSecurityException, IOException {
		final CloudIot service = getService();

		final String registryPath = String.format("projects/%s/locations/%s/registries/%s", projectId, cloudRegion,
				registryName);

		List<Device> devices = service.projects().locations().registries().devices().list(registryPath).execute()
				.getDevices();

		List<Device> result = Lists.newArrayList();
		if (devices != null) {
			logger.debug("Found " + devices.size() + " devices");
			for (Device d : devices) {
				logger.debug("Id: " + d.getId());
				// Fetch details
				Device device = getDevice(d.getId(), projectId, cloudRegion, registryName);
				if (device.getGatewayConfig() != null
						&& "GATEWAY".equalsIgnoreCase(device.getGatewayConfig().getGatewayType())) {
					continue;
				}
				device.setGatewayConfig(null);
				result.add(device);
			}
		} else {
			logger.warn("Registry has no devices.");
		}
		return result;
	}

	/** Retrieves device metadata from a registry. * */
	protected Device getDevice(String deviceId, String projectId, String cloudRegion, String registryName)
			throws GeneralSecurityException, IOException {
		final CloudIot service = getService();

		final String devicePath = String.format("projects/%s/locations/%s/registries/%s/devices/%s", projectId,
				cloudRegion, registryName, deviceId);

		logger.debug("Retrieving device {}", devicePath);
		Device device = service.projects().locations().registries().devices().get(devicePath).execute();
		device.setCredentials(null);
		if (device.getConfig() != null && device.getConfig().getBinaryData() != null) {
			String data = new String(Base64.decodeBase64(device.getConfig().getBinaryData()));
			// device.getConfig().setBinaryData(data);
			JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
			Map<?, ?> map = jsonFactory.fromString(data, Map.class);
			device.set("config2", map);
			logger.debug("config {}", map);
		}
		if (device.getState() != null && device.getState().getBinaryData() != null) {
			String data = new String(Base64.decodeBase64(device.getState().getBinaryData()));
			JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
			Map<?, ?> map = jsonFactory.fromString(data, Map.class);
			device.set("state2", map);
			logger.debug("state {}", map);
		}

		try {
			final InfluxDB influxDB = InfluxDBFactory.connect(serverURL, username, password);
			// FIXME select each device by tag
			influxDB.setDatabase(databaseName);
			String flux = "SELECT mean(temperature) as temperature,max(power) as power,"
					+ "max(min) as min,max(max) as max,max(mode) as mode,"
					+ "max(onMins) as onMins,max(offMins) as offMins,max(wifi) as wifi "
					+ "FROM alldata WHERE time >= now() - 12h GROUP BY time(300s) fill(null)";

			QueryResult queryResult = influxDB.query(new Query(flux));
			if (queryResult.getResults().size() > 0 && queryResult.getResults().get(0).getSeries() != null) {
				device.set("series", queryResult.getResults().get(0).getSeries().get(0));
				logger.trace("queryResult {}", queryResult);
			} else {
				logger.debug("queryResult {}", queryResult);
			}
		} catch (Throwable t) {
			logger.error("Cannot fetch influx data", t);
		}

		return device;
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

	protected void setDeviceConfiguration(String deviceId, String data, long version)
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
