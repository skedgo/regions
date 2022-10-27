package com.buzzhives.validator;


import com.buzzhives.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonParser;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.Cleanup;
import lombok.extern.apachecommons.CommonsLog;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.geotools.geojson.geom.GeometryJSON;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mobilitydata.gtfsvalidator.io.ValidationReportDeserializer;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunner;
import org.mobilitydata.gtfsvalidator.runner.ValidationRunnerConfig;
import org.mobilitydata.gtfsvalidator.util.VersionResolver;
import org.springframework.boot.test.context.SpringBootTest;
import us.dustinj.timezonemap.TimeZone;
import us.dustinj.timezonemap.TimeZoneMap;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.Locale;
import java.util.*;
import java.util.stream.Collectors;

@CommonsLog
@SpringBootTest
class ValidatorApplicationTests {

    @NotNull
    private static final String BASE_DIR = "../../";

    @NotNull
    private static final String SCHEMAS_DIR = BASE_DIR + "schemas/";

    @NotNull
    private static final String REGIONS_DIR = BASE_DIR + "regions/";

    @NotNull
    private static final String PT_STATIC_FEEDS_DIR = BASE_DIR + "pt-static-feeds/";

    @NotNull
    private static final String PT_REALTIME_FEEDS_DIR = BASE_DIR + "pt-realtime-feeds/";

    @NotNull
    private static final String GTFS_VALIDATOR_REPORT_BASE_DIR = "gtfs-validation-reports/";

    @NotNull
    private static final JsonSchema REGION_SCHEMA;

    @NotNull
    private static final JsonSchema PUBLIC_TRANSPORT_FEED_SCHEMA;

    @NotNull
    private static final JsonSchema REAL_TIME_DATA_FEED_SCHEMA;

    private static final Set<String> ISO_LANGUAGES = Arrays.stream(Locale.getISOLanguages()).collect(Collectors.toSet());
    private static final Set<String> ISO_COUNTRIES = Arrays.stream(Locale.getISOCountries()).collect(Collectors.toSet());
    private static final Set<String> ISO_CURRENCIES = Currency.getAvailableCurrencies().stream().map(Currency::getCurrencyCode).collect(Collectors.toSet());


    static {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try {
            REGION_SCHEMA = factory.getSchema(Files.newInputStream(Paths.get(SCHEMAS_DIR + "region.schema.json")));
            PUBLIC_TRANSPORT_FEED_SCHEMA = factory.getSchema(Files.newInputStream(Paths.get(SCHEMAS_DIR + "pt-static-feed.schema.json")));
            REAL_TIME_DATA_FEED_SCHEMA = factory.getSchema(Files.newInputStream(Paths.get(SCHEMAS_DIR + "pt-realtime-feed.schema.json")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<ValidationMessage> validateJson(@NotNull Path path, @NotNull JsonSchema schema) throws IOException {
        val jsonNode = new ObjectMapper().readTree(path.toFile());
        return schema.validate(jsonNode);
    }

    @NotNull
    private static <T> Set<T> validateAndParse(@NotNull Class<T> klass,
                                               @NotNull JsonSchema schema,
                                               @NotNull String directoryPath) throws IOException {
        val set = new HashSet<T>();
        try {
            @Cleanup val regionFiles = Files.list(Paths.get(directoryPath));
            for (val path : regionFiles.collect(Collectors.toSet())) {
                log.info("validating " + path.toString());
                Assertions.assertThat(validateJson(path, schema)).isEmpty();
                val entity = new ObjectMapper().readValue(path.toFile(), klass);
                Assertions.assertThat(path.getFileName().toString()).isEqualTo(getCorrectFileName(entity));
                set.add(entity);
            }
        } catch (NoSuchFileException ignored) { }
        return set;
    }

    @NotNull
    private static String getCorrectFileName(@NotNull Object entity) {
        val fileName = new StringBuilder();
        if (entity instanceof RegionSchema) {
            val code = ((RegionSchema) entity).getCode();
            fileName.append(code.getCountryCode());
            Optional.ofNullable(code.getSubdivisionCode()).filter(sc -> !sc.isEmpty()).map(sc -> fileName.append("-").append(sc));
            fileName.append("-").append(code.getCityName().replaceAll("\\s", ""));
        } else if (entity instanceof PtStaticFeedSchema)
            fileName.append(((PtStaticFeedSchema) entity).getId());
        else if (entity instanceof PtRealtimeFeedSchema)
            fileName.append(((PtRealtimeFeedSchema) entity).getId());
        return fileName.append(".json").toString().toLowerCase();
    }

    @NotNull
    private static String getRegionName(@NotNull Code code) {
        val regionName = new StringBuilder(code.getCityName());
        Optional.ofNullable(code.getSubdivisionCode()).filter(sc -> !sc.isEmpty()).map(sc -> regionName.append(", ").append(sc));
        regionName.append(", ").append(code.getCountryCode());
        return regionName.toString();
    }

    public static boolean isUrlValid(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @Test
    void validate() throws IOException {

        val timeZoneMap = TimeZoneMap.forEverywhere();

        val regions = validateAndParse(RegionSchema.class, REGION_SCHEMA, REGIONS_DIR);
        val publicTransportFeeds = validateAndParse(PtStaticFeedSchema.class, PUBLIC_TRANSPORT_FEED_SCHEMA, PT_STATIC_FEEDS_DIR);
        val realtimeDataFeeds = validateAndParse(PtRealtimeFeedSchema.class, REAL_TIME_DATA_FEED_SCHEMA, PT_REALTIME_FEEDS_DIR);

        val validUrlCondition = new Condition<>(ValidatorApplicationTests::isUrlValid, "a valid URL");


        log.info("verifying pt-realtime-feeds...");
        val realtimeDataFeedsMap = new HashMap<String, PtRealtimeFeedSchema>();
        for (val realtimeDataFeed : realtimeDataFeeds) {
            val id = realtimeDataFeed.getId();
            Assertions.assertThat(id).isNotIn(realtimeDataFeedsMap.keySet());
            realtimeDataFeedsMap.put(id, realtimeDataFeed);
            Assertions.assertThat(realtimeDataFeed.getSource().getUrl()).is(validUrlCondition);
        }

        log.info("verifying pt-static-feeds...");
        val publicTransportFeedsMap = new HashMap<String, PtStaticFeedSchema>();
        for (val publicTransportFeed : publicTransportFeeds) {
            val feedId = publicTransportFeed.getId();
            Assertions.assertThat(feedId).isNotIn(publicTransportFeedsMap.keySet());
            publicTransportFeedsMap.put(feedId, publicTransportFeed);
            val url = publicTransportFeed.getSource().getUrl();
            Assertions.assertThat(url).is(validUrlCondition);

            if (publicTransportFeed.getType() == PtStaticFeedSchema.Type.GTFS)
                validateGtfs(feedId, url);

            Assertions.assertThat(publicTransportFeed.getDataProvider().getUrl()).is(validUrlCondition);

            val apiInformation = publicTransportFeed.getSource().getApiInformation();
            if (apiInformation != null) Assertions.assertThat(apiInformation.getUrl()).is(validUrlCondition);

            val realtime = publicTransportFeed.getRealtime();
            if (realtime != null && !realtime.isEmpty())
                Assertions.assertThat(realtimeDataFeedsMap).containsKeys(realtime.toArray(new String[0]));
        }

        log.info("verifying regions...");
        val gf = new GeometryFactory();
        val geometryJSON = new GeometryJSON();
        val mapper = new ObjectMapper();
        val regionCodes = new HashSet<Code>();
        for (val region : regions) {
            //check the code of the regions and its uniqueness
            val code = region.getCode();
            val regionName = getRegionName(code);
            log.info("verifying region " + regionName);
            Assertions.assertThat(code).as("duplicated region code: " + code).isNotIn(regionCodes);
            regionCodes.add(code);
            Assertions.assertThat(code.getCountryCode()).isIn(ISO_COUNTRIES);
            val locale = region.getLocale();
            Assertions.assertThat(locale.getCountry()).isIn(ISO_COUNTRIES);
            Assertions.assertThat(locale.getLanguage()).isIn(ISO_LANGUAGES);
            Assertions.assertThat(region.getCurrency()).isIn(ISO_CURRENCIES);
            Assertions.assertThat(region.getTimezone()).isIn(ZoneId.getAvailableZoneIds());

            @Cleanup val polygonStream = new ByteArrayInputStream(mapper.writeValueAsString(region.getCoverage().getPolygon()).getBytes());
            val polygon = geometryJSON.readPolygon(polygonStream);

            //check that all cities are contained in the polygon.
            for (val city : region.getCoverage().getCities()) {
                val timeZones = timeZoneMap.getOverlappingTimeZones(city.getLat(), city.getLng());
                Assertions.assertThat(timeZones).isNotNull();
                Assertions.assertThat(timeZones).isNotEmpty();
                val timeZoneIds = timeZones.stream()
                        .map(TimeZone::getZoneId)
                        .collect(Collectors.toSet());
                Assertions.assertThat(region.getTimezone()).isIn(timeZoneIds);
                Assertions.assertThat(polygon.contains(gf.createPoint(new Coordinate(city.getLng(), city.getLat()))))
                        .withFailMessage(String.format("City %s is not contained in %s polygon.", city.getName(), regionName))
                        .isTrue();
            }
            //check that all regions have a valid and existent feed
            val publicTransportFeedRefs = region.getFeeds();
            if (publicTransportFeedRefs != null && !publicTransportFeedRefs.isEmpty())
                Assertions.assertThat(publicTransportFeedsMap).containsKeys(publicTransportFeedRefs.toArray(new String[0]));

            //check for vehicle cost information
            val vehicleCost = region.getVehicleCost();
            if (vehicleCost != null) {

                val averageCostPerLiter = vehicleCost.getAverageCostPerLiter();
                if (averageCostPerLiter != null) {
                    val fuelTypes = new HashSet<AverageCostPerLiter.FuelType>();
                    for (val costPerLiter : averageCostPerLiter)
                        Assertions.assertThat(fuelTypes.add(costPerLiter.getFuelType()))
                                .withFailMessage("fuel cost should be specified once per type")
                                .isTrue();
                }


                val averageCostPerKm = vehicleCost.getAverageCostPerKm();
                if (averageCostPerKm != null) {
                    val vehicleTypes = new HashSet<AverageCostPerKm.VehicleType>();
                    for (val costPerKm : averageCostPerKm)
                        Assertions.assertThat(vehicleTypes.add(costPerKm.getVehicleType()))
                                .withFailMessage("vehicle cost should be specified once per type")
                                .isTrue();
                }
            }
        }

        log.info("no errors detected");
    }

    void validateGtfs(@NotNull String feedId,
                      @NotNull String url) {
        try {
            log.info("validating gtfs -> " + feedId);
            val validationOutputDir = GTFS_VALIDATOR_REPORT_BASE_DIR + File.separator + feedId;
            val runner = new ValidationRunner(new VersionResolver());
            val builder = ValidationRunnerConfig.builder();
            builder.setGtfsSource(new URI(url));
            builder.setOutputDirectory(Path.of(validationOutputDir));
            runner.run(builder.build());
            val jsonElement = JsonParser.parseReader(new FileReader(validationOutputDir + File.separator + "report.json"));
            val validationReport = new ValidationReportDeserializer().deserialize(jsonElement, null, null);
            for (val errorNotice : validationReport.getErrorNotices()) {
                val severity = errorNotice.getSeverity();
                val msg = String.format("    %s, amount: %d", errorNotice.getCode(), errorNotice.getTotalNotices());
                switch (severity){
                    case INFO:
                        log.info(msg);
                        break;
                    case WARNING:
                        log.warn(msg);
                        break;
                    case ERROR:
                        log.error(msg);
                        break;
                }
            }
        } catch (URISyntaxException | FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
