package com.buzzhives.regions;

import com.buzzhives.model.PublicTransportFeedSchema;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@CommonsLog
class RegionsApplicationTests {

    @NotNull
    private static final JsonSchema REGION_SCHEMA;
    @NotNull
    private static final JsonSchema PUBLIC_TRANSPORT_FEED_SCHEMA;

    static {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try {
            REGION_SCHEMA = factory.getSchema(Files.newInputStream(Paths.get("schemas/region.schema.json")));
            PUBLIC_TRANSPORT_FEED_SCHEMA = factory.getSchema(Files.newInputStream(Paths.get("schemas/public-transport-feed.schema.json")));
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
    void valid() throws IOException {
        val regions = validateAndParse(RegionSchema.class, REGION_SCHEMA, "regions");
        val publicTransportFeedsMap = validateAndParse(PublicTransportFeedSchema.class, PUBLIC_TRANSPORT_FEED_SCHEMA, "publictransportfeeds")
                .stream()
                .collect(Collectors.toMap(PublicTransportFeedSchema::getId, publicTransportFeed -> publicTransportFeed, (a, b) -> b, HashMap::new));

        //check that all regions have a valid and existent feed
        for (val region : regions) {
            val publicTransportFeedRefs = region.getFeeds();
            if (publicTransportFeedRefs != null && !publicTransportFeedRefs.isEmpty())  {
                val feedIds = publicTransportFeedRefs.toArray(new String[0]);
                Assertions.assertThat(publicTransportFeedsMap).containsKeys(feedIds);
            }
        }
    }

}
