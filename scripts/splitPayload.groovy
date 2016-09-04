import com.fasterxml.jackson.databind.ObjectMapper

def lines = payload.tokenize('\n')

//def dataOut = ''
def dataOut = []

assert lines.each {
	lineOut = headers.file_name + '|' + it + '\n'
//	jsonPayload << lineOut
//	jsonPayload << JsonOutput.toJson([source: headers.filename, data: it])
//	 dataOut = dataOut + lineOut
	dataOut << lineOut
}

return dataOut
//println mapper.writeValueAsString(jsonPayload)
// What if payload is too big?
