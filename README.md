CXP Stream Ingestion
====================

This project implements a custom processor module named *cxp-stream-processor*. When deployed to a Spring XD container,
the module accepts JSON strings output from the cxp-file-splitter module as input, and outputs events as JSON or CSV
for processing by the next module. This module is backed by a Java class *MetadataDrivenStreamProcessor*. The
parameters accepted by this module are defined in the Java class *StreamProcessorOptionsMetadata*.

## Building with Gradle

    ./gradlew clean build

### Installing or Reinstalling the module

The 'fat jar' will be in `[project-build-dir]/cxp-ingest-stream-1.0.jar`. To install and register the module to your
Spring XD distribution, use the `module upload` Spring XD shell command. Start Spring XD and the shell:

    xd:>module delete processor:cxp-stream-processor
    Successfully destroyed module 'cxp-stream-processor' with type processor

    xd:>module upload --file /home/cxp/big-data-cxp/cxp-ingest-stream/build/libs/cxp-ingest-stream-1.0.jar  --name cxp-stream-processor --type processor
    Successfully uploaded module 'processor:cxp-stream-processor'

    xd:>module info processor:cxp-stream-processor
    Information about processor module 'cxp-stream-processor':

### Getting metadata service running

Because the cxp-stream-processor calls a metadata web service (Metastore) to obtain information about the JSON input
records, the web service must be running before the cxp-stream-processor module is used.

    cd <path-to>/metastore/
    java -jar build/libs/metastore-1.0.jar

Allow a few minutes for the metadata service to start completely before moving onto the next step.

### Logging the output

Create and deploy a stream:

    xd:>stream create --name fileevents --definition "file --dir=/home/cxp/Data/inbox --pattern=*.* --preventDuplicates=false --ref=false --outputType=text/plain | splitter --expression=payload.split('\\n') | script --script=file:///home/cxp/big-data-cxp/cxp-ingest-stream/scripts/jsonPayload.groovy | cxp-stream-processor | log" --deploy

Alternatively use the custom cxp-file-splitter

    xd:>stream create --name fileevents --definition "file --dir=[path-to-data-dir] --pattern=*.* --preventDuplicates=false --ref=true --outputType=text/plain | cxp-file-splitter | script --script=file:///home/cxp/big-data-cxp/cxp-ingest-stream/scripts/jsonPayload.groovy | cxp-stream-processor | log" --deploy

You should see the SQL output in the XD Server log messages.

    2015-05-12 17:20:33,416 1.1.1.RELEASE  INFO task-scheduler-7 sink.fileevents - INSERT INTO events (process_name, customer_id, customer_id_type_id, event_type_id, value, ts, created_ts) values ( ...

Run the following command to delete the test stream.

    xd:>stream destroy --name fileevents

### Using custom module parameters

To set a custom delimiter for line output, create and deploy a new stream 'myevents'

    xd:>stream create --name myevents --definition "file --dir=/home/cxp/Data/inbox --pattern=*.* --preventDuplicates=false --ref=false --fixedDelay=10 --outputType=text/plain | metadata-retriever | splitter --expression=payload.split(headers.get('row_delimiter')) | script --script=file:///home/cxp/big-data-cxp/cxp-ingest-stream/scripts/jsonPayload.groovy | cxp-stream-processor --columnDelimiter='|' | log " --deploy
