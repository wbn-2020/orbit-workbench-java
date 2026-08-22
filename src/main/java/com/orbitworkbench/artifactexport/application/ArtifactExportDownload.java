package com.orbitworkbench.artifactexport.application;

import java.io.InputStream;
import org.springframework.http.MediaType;

public record ArtifactExportDownload(
        InputStream inputStream,
        String fileName,
        MediaType mediaType,
        long sizeBytes
) {
}
