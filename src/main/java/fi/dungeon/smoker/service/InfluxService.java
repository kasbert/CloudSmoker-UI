package fi.dungeon.smoker.service;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.dto.QueryResult.Series;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InfluxService {
	Logger logger = LoggerFactory.getLogger(InfluxService.class);

	@Value("${influx.url}")
	private String serverURL = "http://localhost:8086";
	@Value("${influx.username}")
	private String username = "admin";
	@Value("${influx.password}")
	private String password;
	@Value("${influx.database}")
	private String databaseName = "iot";

	/** Retrieves device metadata from a registry. * */
	public Series getDeviceSeries(String deviceId) {

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
				logger.trace("queryResult {}", queryResult);
				return queryResult.getResults().get(0).getSeries().get(0);
			} else {
				logger.debug("queryResult {}", queryResult);
			}
		} catch (Throwable t) {
			logger.error("Cannot fetch influx data", t);
		}

		return new Series();
	}

}
