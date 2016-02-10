package c8y.trackeragent.protocol.telic.parser;

import java.math.BigDecimal;
import java.util.Date;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cumulocity.rest.representation.event.EventRepresentation;
import com.cumulocity.sdk.client.SDKException;

import c8y.Position;
import c8y.trackeragent.Parser;
import c8y.trackeragent.TrackerAgent;
import c8y.trackeragent.TrackerDevice;
import c8y.trackeragent.context.ReportContext;
import c8y.trackeragent.protocol.telic.TelicConstants;
import c8y.trackeragent.service.MeasurementService;

/**
 * <p>
 * Location report of the Telic tracker.
 * </p>
 * 
 * <pre>
 * 072118718299,200311121210,0,200311121210,115864,480332,3,4,67,4,,,599,11032,,010 1,00,238,0,0,0
 * </pre>
 * 
 */
@Component
public class TelicLocationReport implements Parser, TelicFragment {

    private static Logger logger = LoggerFactory.getLogger(TelicLocationReport.class);

    private static final int LOG_CODE = 0;

    private static final int LOG_TIMESTAMP = 1;

    private static final int GPS_TIMESTAMP = 3;

    private static final int LONGITUDE = 4;

    private static final int LATITUDE = 5;

    private static final int FIX_TYPE = 6;

    private static final int SPEED = 7;

    private static final int SATELLITES_FOR_CALCULATION = 9;

    public static final int ALTITUDE = 12;

    public static final BigDecimal LAT_AND_LNG_DIVISOR = new BigDecimal(10000);

    private TrackerAgent trackerAgent;

    private MeasurementService measurementService;

    @Autowired
    public TelicLocationReport(TrackerAgent trackerAgent, MeasurementService measurementService) {
        this.trackerAgent = trackerAgent;
        this.measurementService = measurementService;
    }

    @Override
    public String parse(String[] report) throws SDKException {
        return report[0].substring(4, 10);
    }

    @Override
    public boolean onParsed(ReportContext reportCtx) throws SDKException {
        logger.info("Parse position for telic tracker");
        TrackerDevice device = trackerAgent.getOrCreateTrackerDevice(reportCtx.getImei());
        EventRepresentation locationUpdateEvent = device.aLocationUpdateEvent();
        Position position = new Position();
        DateTime dateTime = getGPSTimestamp(reportCtx);
        if (dateTime == null) {
            dateTime = new DateTime();
        } else {
            position.setProperty(TelicConstants.GPS_TIMESTAMP, dateTime.toDate());
        }
        
        position.setLat(getLatitude(reportCtx));
        position.setLng(getLongitue(reportCtx));
        BigDecimal altitude = getAltitude(reportCtx);
        if (altitude != null) {
            position.setAlt(altitude);
            measurementService.createAltitudeMeasurement(altitude, device, dateTime);
        }
        LogCodeType logCodeType = getLogCodeType(reportCtx);
        if (logCodeType != null) {
            position.setProperty(TelicConstants.LOG_CODE_TYPE, logCodeType.getLabel());
        }
        Date logTimestamp = getLogTimestamp(reportCtx).toDate();
        if (logTimestamp != null) {
            position.setProperty(TelicConstants.LOG_TIMESTAMP, logTimestamp);
        }
        locationUpdateEvent.setTime(dateTime.toDate());
        FixType fixType = getFixType(reportCtx);
        if (fixType != null) {
            position.setProperty(TelicConstants.FIX_TYPE, fixType.getLabel());
        }
        Integer satellitesForCalculation = getSatellitesForCalculation(reportCtx);
        if (satellitesForCalculation != null) {
            position.setProperty(TelicConstants.SATELLITES, satellitesForCalculation);
        }

        device.setPosition(locationUpdateEvent, position);

        BigDecimal speed = getSpeed(reportCtx);
        if (speed != null) {
            measurementService.createSpeedMeasurement(speed, device, dateTime);
        }
        return true;
    }

    private LogCodeType getLogCodeType(ReportContext reportCtx) {
        String codeStr = reportCtx.getEntry(LOG_CODE).substring(10);
        if ("99".equals(codeStr)) {
            return LogCodeType.TIME_EVENT;
        } else if ("98".equals(codeStr)) {
            return LogCodeType.DISTANCE_EVENT;
        }
        //TODO
        logger.warn("Cant establish event code for value {} in report {}", codeStr, reportCtx);
        return null;
    }

    private DateTime getLogTimestamp(ReportContext reportCtx) {
        return getTimestamp(reportCtx, LOG_TIMESTAMP);
    }

    private DateTime getGPSTimestamp(ReportContext reportCtx) {
        return getTimestamp(reportCtx, GPS_TIMESTAMP);
    }

    private DateTime getTimestamp(ReportContext reportCtx, int index) {
        String timestampStr = reportCtx.getEntry(index);
        return timestampStr == null ? null : TelicConstants.TIMESTAMP_FORMATTER.parseDateTime(timestampStr);
    }

    private BigDecimal getLongitue(ReportContext reportCtx) {
        return getCoord(reportCtx, LONGITUDE);
    }

    private BigDecimal getLatitude(ReportContext reportCtx) {
        return getCoord(reportCtx, LATITUDE);
    }

    private BigDecimal getAltitude(ReportContext reportCtx) {
        return new BigDecimal(reportCtx.getEntry(ALTITUDE));
    }

    private BigDecimal getCoord(ReportContext reportCtx, int index) {
        BigDecimal incomingValue = new BigDecimal(reportCtx.getEntry(index));
        return incomingValue.divide(LAT_AND_LNG_DIVISOR);
    }

    private FixType getFixType(ReportContext reportCtx) {
        String fixTypeStr = reportCtx.getEntry(FIX_TYPE);
        FixType fixType = FixType.forValue(fixTypeStr);
        if (fixType == null) {
            logger.warn("Cant establish fix type for value {} in report {}", fixTypeStr, reportCtx);
        }
        return fixType;
    }

    private BigDecimal getSpeed(ReportContext reportCtx) {
        return reportCtx.getEntryAsNumber(SPEED);
    }

    private Integer getSatellitesForCalculation(ReportContext reportCtx) {
        return reportCtx.getEntryAsInt(SATELLITES_FOR_CALCULATION);
    }
}
