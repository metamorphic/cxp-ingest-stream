package cxp.ingest;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Aung Htet
 */
public class MetadataDrivenStreamProcessor {

    private static final Log log = LogFactory.getLog(MetadataDrivenStreamProcessor.class);

    private static final String LINE_OUTPUT_FORMAT = "LINE";

    private static final String JSON_OUTPUT_FORMAT = "JSON";

    MetadataProvider metadataProvider;

    MetadataDrivenItemTransformer transformer;

    FileDataset fileDataset;
    ObjectMapper mapper;

    // Options
    // The following provided as command line arguments or
    // using defaults set by StreamProcessorOptionsMetadata

    String metastoreApiUrl;

    String outputFormat = "LINE";   // LINE OR JSON

    String columnDelimiter = "\t";  // Does not apply if output is JSON

    int metadataCacheLife = 15;     // minutes to cache metadata


    Map<String, String> metadataCache;
    Map<String, LocalDateTime> metadataCacheExpiry;

    public MetadataDrivenStreamProcessor(String metastoreApiUrl, String outputFormat,
                                         String columnDelimiter, int metadataCacheLife) {
        this.metastoreApiUrl = metastoreApiUrl;
        mapper = new ObjectMapper();
        metadataCache = new HashMap<String, String>();
        metadataCacheExpiry = new HashMap<String, LocalDateTime>();

        if (LINE_OUTPUT_FORMAT.equals(outputFormat) || JSON_OUTPUT_FORMAT.equals(outputFormat))
            this.outputFormat = outputFormat;

        if (!columnDelimiter.isEmpty())
            this.columnDelimiter = columnDelimiter;

        if (metadataCacheLife >= 0)
            this.metadataCacheLife = metadataCacheLife;
    }

    public List<String> process(String data) throws Exception {

        Pattern pattern = Pattern.compile(",\\s*\"data\":\\s*[\\[\\{]");
        Matcher matcher = pattern.matcher(data);

        String filename;
        String payload;
        boolean isJson = false;

        if (matcher.find()) {
            JsonNode root = mapper.readTree(data);
            filename = root.path("filename").getTextValue();
            payload = root.path("data").toString();
            isJson = true;
        } else {
            // Read input JSON as Map
            Map<String, String> itemMap = mapper.readValue(data, new TypeReference<HashMap<String, String>>() {});
            filename = itemMap.get("filename");
            payload = itemMap.get("data");
        }

        // Output payload will be a list of messages for each event
        List<String> payloadMsgs = new ArrayList<String>();

        LocalDateTime now = LocalDateTime.now();

        if (log.isDebugEnabled()) {
            log.debug("Finding metadata for: " + filename);
        }

        // Check if metadata is already in cache
        if (metadataCache.containsKey(filename) && now.isBefore(metadataCacheExpiry.get(filename))) {
            if (log.isDebugEnabled()) {
                log.debug("Metadata found in cache");
            }
            String metadata = metadataCache.get(filename);
            fileDataset = mapper.readValue(metadata, FileDataset.class);

            Assert.notNull(fileDataset);

        } else {
            if (log.isDebugEnabled()) {
                log.debug("Metadata received from metastore");
            }

            // Call metadata service and set cache
            metadataProvider.setFilename(filename);
            fileDataset = metadataProvider.getFileDataset();

            Assert.notNull(fileDataset);

            metadataCache.put(filename, mapper.writeValueAsString(fileDataset));
            metadataCacheExpiry.put(filename, now.plusMinutes(metadataCacheLife));
        }

        if (fileDataset.getEventTypes() == null) {
            log.warn("No events found in metadata");
            return payloadMsgs;
        }
        if (fileDataset.getColumns() == null) {
            log.warn("No columns found in metadata");
            return payloadMsgs;
        }

        if (metadataProvider.isJson() != isJson) {
            log.warn("Payload does not match metadata description");
            log.debug(payload);
        }

        List<CustomerEvent> customerEvents;

        if (metadataProvider.isJson()) {
            customerEvents = transformer.transform(payload);
        } else {
            // Construct Map from string input
            char quotechar = getQuoteChar(fileDataset.getTextQualifier());
            CSVParser csvParser = new CSVParser(fileDataset.getColumnDelimiter(), quotechar);
            CSVReader reader = new CSVReader(new StringReader(payload), 0, csvParser);
            String[] record = reader.readNext();

            // do nothing if record is null
            if (record == null) {
                log.warn("Data record is null");
                return payloadMsgs;
            }

            Map<String, Object> item = new HashMap<String, Object>();

            // populate entries with input data
            String[] columnNames = fileDataset.getColumnNames();
            for (int i = 0; i < columnNames.length; i++) {
                item.put(columnNames[i], record[i]);
            }

            customerEvents = transformer.transform(item);
        }

        DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
        if (log.isDebugEnabled()) {
            log.debug("Output format is " + outputFormat);
        }

        for (CustomerEvent event : customerEvents) {

            if (JSON_OUTPUT_FORMAT.equals(outputFormat)) {
                // Create Map for JSON output
                Map<String, Object> outMap = new HashMap<String, Object>();
                outMap.put("customerIdTypeId", event.getCustomerIdTypeId());
                outMap.put("customer_id", event.getCustomerId());
                outMap.put("event_type_id", event.getEventTypeId());
                outMap.put("ts", fmt.print(event.getTs()));
                outMap.put("event_version", "1");
                outMap.put("value", event.getValue());
                outMap.put("job_id", "0");
                outMap.put("process_name", "cxp-stream-processor");
                outMap.put("created_ts", fmt.print(LocalDateTime.now().toDateTime()));
                payloadMsgs.add(mapper.writeValueAsString(outMap));

            } else { // outputFormat == 'LINE'
                // Add fields to create delimited string with fixed order of fields
                List<String> outFields = new ArrayList<String>();
                outFields.add(String.valueOf(event.getCustomerIdTypeId()));
                outFields.add(event.getCustomerId());
                outFields.add(String.valueOf(event.getEventTypeId()));
                outFields.add(fmt.print(event.getTs()));
                outFields.add("1");
                outFields.add(event.getValue().toString());
                outFields.add("0");
                outFields.add("cxp-stream-processor");
                outFields.add(fmt.print(LocalDateTime.now().toDateTime()));
                payloadMsgs.add(StringUtils.collectionToDelimitedString(outFields, columnDelimiter));
            }
        }
        return payloadMsgs;
    }

    private char getQuoteChar(String textQualifier) {
        return (textQualifier == null || textQualifier.trim().isEmpty() ?
                CSVParser.DEFAULT_QUOTE_CHARACTER : textQualifier.charAt(0));
    }

    public void setMetadataProvider(MetadataProvider metadataProvider) {
        if (metastoreApiUrl != null && !metastoreApiUrl.isEmpty()) {
            metadataProvider.setDatasetUrl(metastoreApiUrl);
        }
        this.metadataProvider = metadataProvider;
    }

    public void setTransformer(MetadataDrivenItemTransformer transformer) {
        this.transformer = transformer;
    }
}
