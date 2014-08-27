package iamedu.raml.exception

import com.google.gson.Gson
import org.eel.kitchen.jsonschema.report.ValidationReport


class RamlResponseValidationException extends RuntimeException {
  String method
  String serviceName
  Integer statusCode
  String mimeType
  String body
  ErrorReason reason
  Map jsonError
  ValidationReport validationReport

  enum ErrorReason {
    INVALID_BODY,
    INVALID_STATUS_CODE,
    INVALID_MIME_TYPE,
    INVALID_RESPONSE_BODY,
    SCHEMA_NOT_DEFINED
  }

  RamlResponseValidationException(String message, String serviceName, String method, Integer statusCode, String mimeType, String body, ErrorReason reason) {
    super(message)
    this.method = method
    this.serviceName = serviceName
    this.statusCode = statusCode
    this.body = body
    this.reason = reason
  }

  RamlResponseValidationException(String message, String serviceName, String method, Integer statusCode, String mimeType, String body, ErrorReason reason, ValidationReport validationReport) {
    this(message, serviceName, method, statusCode, body, mimeType, reason)
    this.validationReport = validationReport
    if(validationReport) {
      Gson gson = new Gson()
      def error = gson.toJson(validationReport.asJsonNode().toString(), HashMap)
      this.jsonError = error
    }
  }

}
