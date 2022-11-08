package com.buzzhives.validator;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
public class FeedReport {

    @NotNull
    private final String id;
    private final int warnings;
    private final int errors;

    @NotNull
    public String getReportMessage() {
        return String.format("Source %s has %d errors detected and ignored, %d warnings detected and ignored.", id, errors, warnings);
    }

}
