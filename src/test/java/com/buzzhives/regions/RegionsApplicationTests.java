package com.buzzhives.regions;

import com.buzzhives.model.Code;
import com.buzzhives.model.PublicTransportFeedSchema;
import com.buzzhives.model.RealTimeDataFeedSchema;
import com.buzzhives.model.RegionSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.Cleanup;
import lombok.extern.apachecommons.CommonsLog;
import lombok.val;
import org.apache.commons.validator.routines.UrlValidator;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@CommonsLog
class RegionsApplicationTests {

    @NotNull
    private static final JsonSchema REGION_SCHEMA;

    @NotNull
    private static final JsonSchema PUBLIC_TRANSPORT_FEED_SCHEMA;

    @NotNull
    private static final JsonSchema REAL_TIME_DATA_FEED_SCHEMA;

    private static final Set<String> ISO_LANGUAGES = Arrays.stream(Locale.getISOLanguages()).map(String::toLowerCase).collect(Collectors.toSet());
    private static final Set<String> ISO_COUNTRIES = Arrays.stream(Locale.getISOCountries()).map(String::toLowerCase).collect(Collectors.toSet());
    private static final Set<String> ISO_CURRENCIES = Currency.getAvailableCurrencies().stream().map(Currency::getCurrencyCode).collect(Collectors.toSet());


    static {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try {
            REGION_SCHEMA = factory.getSchema(Files.newInputStream(Paths.get("schemas/region.schema.json")));
            PUBLIC_TRANSPORT_FEED_SCHEMA = factory.getSchema(Files.newInputStream(Paths.get("schemas/pt-static-feed.schema.json")));
            REAL_TIME_DATA_FEED_SCHEMA = factory.getSchema(Files.newInputStream(Paths.get("schemas/pt-realtime-feed.schema.json")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void singleRegionTest() throws IOException {
        val jsonNode = new ObjectMapper().readTree(new File("regions/ar_b_bahiablanca.json"));
        val errors = REGION_SCHEMA.validate(jsonNode);
        Assertions.assertThat(errors).isEmpty();
        log.info(" no errors found. ");
    }

    @Test
    void singlePublicTransportDataFeedTest() throws IOException {
        val jsonNode = new ObjectMapper().readTree(new File("publictransportfeeds/ar-sapem.json"));
        val errors = PUBLIC_TRANSPORT_FEED_SCHEMA.validate(jsonNode);
        Assertions.assertThat(errors).isEmpty();
        log.info(" no errors found. ");
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


        val regions = validateAndParse(RegionSchema.class, REGION_SCHEMA, "regions");
        val publicTransportFeeds = validateAndParse(PublicTransportFeedSchema.class, PUBLIC_TRANSPORT_FEED_SCHEMA, "publictransportfeeds");
        val realtimeDataFeeds = validateAndParse(RealTimeDataFeedSchema.class, REAL_TIME_DATA_FEED_SCHEMA, "realtimedatafeeds");

        val validUrlCondition = new Condition<String>(s -> new UrlValidator().isValid(s), "a valid URL");


        val realtimeDataFeedsMap = new HashMap<String, RealTimeDataFeedSchema>();
        for (val realtimeDataFeed : realtimeDataFeeds) {
            val id = realtimeDataFeed.getId();
            Assertions.assertThat(id).isNotIn(realtimeDataFeedsMap.keySet());
            realtimeDataFeedsMap.put(id, realtimeDataFeed);
            Assertions.assertThat(realtimeDataFeed.getSource().getUrl()).is(validUrlCondition);
        }


        val publicTransportFeedsMap = new HashMap<String, PublicTransportFeedSchema>();

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


        val regionCodes = new HashSet<Code>();
        for (val region : regions) {
            //check the code of the regions and its uniqueness
            val code = region.getCode();
            Assertions.assertThat(code).as("duplicated region code: " + code.toString()).isNotIn(regionCodes);
            regionCodes.add(code);
            Assertions.assertThat(code.getCountryCode()).isIn(ISO_COUNTRIES);
            val locale = region.getLocale();
            Assertions.assertThat(locale.getCountry()).isIn(ISO_COUNTRIES);
            Assertions.assertThat(locale.getLanguage()).isIn(ISO_LANGUAGES);
            Assertions.assertThat(region.getCurrency()).isIn(ISO_CURRENCIES);
            Assertions.assertThat(region.getTimezone()).isIn(ZoneId.getAvailableZoneIds());

            //check that all regions have a valid and existent feed
            val publicTransportFeedRefs = region.getFeeds();
            if (publicTransportFeedRefs != null && !publicTransportFeedRefs.isEmpty())
                Assertions.assertThat(publicTransportFeedsMap).containsKeys(publicTransportFeedRefs.toArray(new String[0]));
        }
    }

}
