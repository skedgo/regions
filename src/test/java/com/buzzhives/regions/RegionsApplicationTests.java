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
import java.util.HashSet;
import java.util.Set;
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

    private Set<ValidationMessage> validateJson(@NotNull Path path, @NotNull JsonSchema schema) throws IOException {
        val jsonNode = new ObjectMapper().readTree(path.toFile());
        return schema.validate(jsonNode);
    }

    @Test
    void valid() throws IOException {

        val regions = new HashSet<RegionSchema>();

        @Cleanup val regionFiles = Files.list(Paths.get("regions"));
        for (val path : regionFiles.collect(Collectors.toSet())) {
            log.info("Validating " + path.toString());
            val errors = validateJson(path, REGION_SCHEMA);
            Assertions.assertThat(errors).isEmpty();
            val region = new ObjectMapper().readValue(path.toFile(), RegionSchema.class);
            regions.add(region);
        }

        log.info(regions.size() + " regions validated.");

        val publicTransportFeeds = new HashSet<PublicTransportFeedSchema>();

        @Cleanup val publicTransportFeedsFiles = Files.list(Paths.get("publictransportfeeds"));
        for (val path : publicTransportFeedsFiles.collect(Collectors.toSet())) {
            log.info("Validating " + path.toString());
            val errors = validateJson(path, PUBLIC_TRANSPORT_FEED_SCHEMA);
            Assertions.assertThat(errors).isEmpty();
            val publicTransportFeedSchema = new ObjectMapper().readValue(path.toFile(), PublicTransportFeedSchema.class);
            publicTransportFeeds.add(publicTransportFeedSchema);
        }

        log.info(publicTransportFeeds.size() + " public transport feeds validated.");
    }

}
