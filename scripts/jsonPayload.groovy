import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;

def json = new JsonBuilder()

// test if payload string is json
if (payload ==~ /(?s)^\s*[\[\{].*/) {
    payloadJson = new JsonSlurper().parseText(payload)
    json filename: headers.file_name, data: payloadJson
} else {
    json filename: headers.file_name, data: payload
}

//println headers.sequenceNumber
//println payload

return json.toString()

//REFERENCE
//http://stackoverflow.com/questions/19225600/how-to-construct-json-using-jsonbuilder-with-key-having-the-name-of-a-variable-a
