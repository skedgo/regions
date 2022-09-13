package com.buzzhives.validator;


import com.buzzhives.model.Code;
import com.buzzhives.model.PtRealtimeFeedSchema;
import com.buzzhives.model.PtStaticFeedSchema;
import com.buzzhives.model.RegionSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
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
        @Cleanup val regionFiles = Files.list(Paths.get(directoryPath));
        for (val path : regionFiles.collect(Collectors.toSet())) {
            log.info("Validating " + path.toString());
            Assertions.assertThat(validateJson(path, schema)).isEmpty();
            set.add(new ObjectMapper().readValue(path.toFile(), klass));
        }
        return set;
    }

    @Test
    void validate() throws IOException {


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
            Assertions.assertThat(publicTransportFeed.getId()).isNotIn(publicTransportFeedsMap.keySet());
            publicTransportFeedsMap.put(publicTransportFeed.getId(), publicTransportFeed);
            Assertions.assertThat(publicTransportFeed.getSource().getUrl()).is(validUrlCondition);
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
            for (val city : region.getCoverage().getCities())
                Assertions.assertThat(polygon.contains(gf.createPoint(new Coordinate(city.getLng(), city.getLat()))))
                        .withFailMessage(String.format("City %s is not contained in %s polygon.", city.getName(), regionName))
                        .isTrue();

            //check that all regions have a valid and existent feed
            val publicTransportFeedRefs = region.getFeeds();
            if (publicTransportFeedRefs != null && !publicTransportFeedRefs.isEmpty())
                Assertions.assertThat(publicTransportFeedsMap).containsKeys(publicTransportFeedRefs.toArray(new String[0]));
        }

        log.info("no errors detected");
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

}
