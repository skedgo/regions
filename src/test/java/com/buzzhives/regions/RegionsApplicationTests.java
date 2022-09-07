package com.buzzhives.regions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import lombok.extern.apachecommons.CommonsLog;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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

}
