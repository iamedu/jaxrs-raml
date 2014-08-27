package iamedu.raml.exception.handlers

import com.google.gson.Gson
import iamedu.raml.exception.*


class RamlDefaultRequestExceptionHandler implements RamlRequestExceptionHandler {

  @Override
  RamlErrorResponse handleRequestException(RamlRequestException exception) {
    def jsonError
    if(exception.jsonError) {
      jsonError = new java.util.HashMap(exception.jsonError)
    } else {
      jsonError = new java.util.HashMap()
    }
    request
    if(exception.body) {
      Gson gson = new Gson()
      jsonError.requestBody = gson.toJson(exception.body, HashMap)
    }
    def errorResponse = new RamlErrorResponse([message: exception.message,
                                               errorCode: "invalidRequest",
                                               errorMeta: jsonError])
    errorResponse
  }
}
