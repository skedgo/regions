package com.buzzhives.regions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import lombok.extern.apachecommons.CommonsLog;
import lombok.val;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;

@CommonsLog
class RegionsApplicationTests {

    @Test
    void validJson() throws IOException {
        val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
        val jsonSchema = factory.getSchema(new FileInputStream("schemas/region.schema.json"));
        val jsonNode = new ObjectMapper().readTree("{\n" +
                "\t\"code\": \"ar_b_bahiablanca\",\n" +
                "\t\"timezone\": \"America/Argentina/Buenos_Aires\",\n" +
                "\t\"locale\": {\n" +
                "\t\t\"language\": \"es\",\n" +
                "\t\t\"country\": \"ar\"\n" +
                "\t},\n" +
                "\t\"currency\": {\n" +
                "\t\t\"code\": \"ars\",\n" +
                "\t\t\"symbol\": \"$\",\n" +
                "\t\t\"format\": {\n" +
                "\t\t\t\"unit\": \"$0\",\n" +
                "\t\t\t\"subunit\": \"$0.00\"\n" +
                "\t\t}\n" +
                "\t},\n" +
                "\t\"coverage\": {\n" +
                "\t\t\"defaultLocation\": {\n" +
                "\t\t\t\"lat\": 0,\n" +
                "\t\t\t\"lng\": 0,\n" +
                "\t\t\t\"address\": \"somewhere\"\n" +
                "\t\t},\n" +
                "\t\t\"polygon\": \" lat longs here \"\n" +
                "\t},\n" +
                "\t\"servers\": [ \"GRANDUNI\" ],\n" +
                "\t\"feeds\": [\"SAPEM\"]\n" +
                "}");
        val errors = jsonSchema.validate(jsonNode);
        Assertions.assertThat(errors).isEmpty();
        log.info(" no errors found. ");
    }

}
