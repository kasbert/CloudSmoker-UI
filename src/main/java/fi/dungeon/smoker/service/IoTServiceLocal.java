
package fi.dungeon.smoker.service;

import org.apache.commons.lang3.ObjectUtils.Null;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.cloud.gcp.core.GcpProjectIdProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.google.api.client.util.Lists;
import com.google.api.client.util.Maps;


import fi.dungeon.smoker.entity.Device;


@Service("iotServiceLocal")
@Lazy(true)
//@ConditionalOnProperty(prefix = "app.iot", name = "impl", havingValue = "local", matchIfMissing = true)
public class IoTServiceLocal implements IoTService, IMqttMessageListener {
	Logger logger = LoggerFactory.getLogger(IoTServiceLocal.class);

	@Value("${mqtt.url}")
	private String serverURL;
	@Value("${mqtt.username}")
	private String username = "";
	@Value("${mqtt.password}")
	private String password = "";

	private IMqttClient client;

	private Map<String,Device> devices = Maps.newHashMap();
	private Pattern pattern = Pattern.compile("/devices/([^/]*)/(.*)");


	@PostConstruct
	public synchronized void connect() throws Exception {
		if (client != null && client.isConnected()) {
			logger.debug("Why are we here second time ? {} {} {}", serverURL, this, client);
			return;
		}
		logger.debug("Connecting to {}", serverURL);
		String publisherId = UUID.randomUUID().toString();
		client = new MqttClient(serverURL, publisherId);
		MqttConnectOptions connOpts = new MqttConnectOptions();
		connOpts.setCleanSession(true);
		connOpts.setAutomaticReconnect(true);
		connOpts.setUserName(username);
		connOpts.setPassword(password.toCharArray());
		client.connect(connOpts);

		client.subscribe("/devices/#", this);
	}

	@PreDestroy
	public void disconnect() throws Exception {
		logger.debug("Disconnecting mqtt {}", this);
		client.unsubscribe("/devices/#");
		client.disconnect();
	}

	public void messageArrived(String topic, MqttMessage message) throws Exception {
		byte[] payload = message.getPayload();
		logger.debug("Received {} {} {}", topic, message.getQos(), message);
		Matcher m = pattern.matcher(topic);
		if (!m.matches()) {
			return;
		}
		String name = m.group(1);
		String item = m.group(2);
		String str = new String(payload);
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		// FIXME only allowed parameters!
		Map<String,String> map = new HashMap((Map<String,String>)mapper.readValue(str, Map.class));
		synchronized(devices) {
			if (!devices.containsKey(name)) {
				logger.info("Found device '{}'' {}", name, item);
				devices.put(name, new Device());
			}
			Device device = devices.get(name);
			device.put("id", name);
			if (item.equals("config")) {
				map.put("cloudUpdateTime",Instant.now().toString());
				device.put("config", map);
			} else if (item.equals("state")) {
				map.put("updateTime",Instant.now().toString());
				device.put("state", map);
			} else if (item.equals("events")) {
				device.put("events", map);
			}
		}
		if (item.equals("events") || item.equals("state")) {
			client.messageArrivedComplete(message.getId(), message.getQos());
		}
	}


	public List<Device> listDevices() throws Exception {
		synchronized(devices) {
			List<Device> result = Lists.newArrayList(devices.values());
			return result;
		}
	}

	public Device getDevice(String deviceId) throws GeneralSecurityException, IOException {
		synchronized(devices) {
			Device result = devices.get(deviceId);
			return result;
		}
	}

	public void setDeviceConfiguration(String deviceId, String data, long version)
			throws Exception {
		logger.info("Set configuration for {}", deviceId);
		String topic = "/devices/" + deviceId + "/config";
		client.publish(topic, data.getBytes(), 0, true);
	}
}
