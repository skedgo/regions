package com.buzzhives.regions;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;
import java.util.stream.Collectors;

@CommonsLog
class RegionsApplicationTests {

    @Test
    void validRegionJson() throws IOException {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        val jsonSchema = factory.getSchema(Files.newInputStream(Paths.get("schemas/region.schema.json")));
        val jsonNode = new ObjectMapper().readTree(new File("regions/ar_b_bahiablanca.json"));
        val errors = jsonSchema.validate(jsonNode);
        Assertions.assertThat(errors).isEmpty();
        log.info(" no errors found. ");
    }

    @Test
    void validPublicTransportDataFeedJson() throws IOException {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        val jsonSchema = factory.getSchema(Files.newInputStream(Paths.get("schemas/public-transport-feed.schema.json")));
        val jsonNode = new ObjectMapper().readTree(new File("publictransportfeeds/ar-sapem.json"));
        val errors = jsonSchema.validate(jsonNode);
        Assertions.assertThat(errors).isEmpty();
        log.info(" no errors found. ");
    }

    private Set<ValidationMessage> validRegion(@NotNull Path path) throws IOException {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        val jsonSchema = factory.getSchema(Files.newInputStream(Paths.get("schemas/region.schema.json")));
        val jsonNode = new ObjectMapper().readTree(path.toFile());
        return jsonSchema.validate(jsonNode);
    }

    private Set<ValidationMessage> validPublicTransportFeed(@NotNull Path path) throws IOException {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        val jsonSchema = factory.getSchema(Files.newInputStream(Paths.get("schemas/public-transport-feed.schema.json")));
        val jsonNode = new ObjectMapper().readTree(path.toFile());
        return jsonSchema.validate(jsonNode);
    }

    @Test
    void valid() throws IOException {
        @Cleanup val regions = Files.list(Paths.get("regions"));

        for (val path : regions.collect(Collectors.toSet())) {
            log.info("Validating " + path.toString());
            val errors = validRegion(path);
            Assertions.assertThat(errors).isEmpty();
        }

        @Cleanup val publicTransportFeeds = Files.list(Paths.get("publictransportfeeds"));
        for (val path : publicTransportFeeds.collect(Collectors.toSet())) {
            log.info("Validating " + path.toString());
            val errors = validPublicTransportFeed(path);
            Assertions.assertThat(errors).isEmpty();
        }
    }

}
