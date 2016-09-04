package cxp.ingest;

import org.springframework.xd.module.options.spi.ModuleOption;

public class StreamProcessorOptionsMetadata {

    private String metastoreApiUrl = null;

    private String outputFormat = "LINE";   // LINE | JSON

    private String columnDelimiter = "\t";  // n/a if output format is JSON

    private int metadataCacheLife = 15;     // 15 minutes

    public String getMetastoreApiUrl() {
        return metastoreApiUrl;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public String getColumnDelimiter() {
        return columnDelimiter;
    }

    public int getMetadataCacheLife() {
        return metadataCacheLife;
    }

    @ModuleOption("Metastore search-by-filename API URL")
    public void setMetastoreApiUrl(String metastoreApiUrl) {
        this.metastoreApiUrl = metastoreApiUrl;
    }

    @ModuleOption("Format of payload output. Must be either LINE or JSON.")
    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    @ModuleOption("Delimiter character if chosen LINE as output format.")
    public void setColumnDelimiter(String columnDelimiter) {
        this.columnDelimiter = columnDelimiter;
    }

    @ModuleOption("How long (in minutes) the metadata for a file should be cached before accessing Metastore again.")
    public void setMetadataCacheLife(int metadataCacheLife) {
        this.metadataCacheLife = metadataCacheLife;
    }
}
