import com.fasterxml.jackson.databind.ObjectMapper

mapper = new ObjectMapper()
return mapper.writeValueAsString(payload)

//return mapper.writeValueAsString(headers) + payload
//return mapper.writeValueAsString(headers)
