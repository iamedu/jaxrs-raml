package iamedu.raml.exception

import com.google.gson.Gson
import org.eel.kitchen.jsonschema.report.ValidationReport

class RamlRequestException extends RuntimeException {
  String method
  String requestUrl
  String body
  Map jsonError
  ValidationReport validationReport

  RamlRequestException(String message, String requestUrl) {
    super(message)
    this.requestUrl = requestUrl
  }

  RamlRequestException(String message, String requestUrl, String method) {
    this(message, requestUrl)
    this.method = method
  }

  RamlRequestException(String message, String requestUrl, String method, ValidationReport validationReport, String body) {
    this(message, requestUrl)
    this.method = method
    this.body = body
    this.validationReport = validationReport
    Gson gson = new Gson()
    if(validationReport) {
      def error = gson.toJson(validationReport.asJsonNode().toString(), HashMap)
      this.jsonError = error
    }
  }

}
