package iamedu.raml.validator

import com.google.gson.Gson
import com.google.gson.JsonParser
import iamedu.raml.exception.RamlRequestException
import iamedu.raml.exception.RamlResponseValidationException
import org.apache.commons.lang.WordUtils
import org.commonjava.mimeparse.MIMEParse
import org.eel.kitchen.jsonschema.main.JsonSchemaFactory
import org.eel.kitchen.jsonschema.util.JsonLoader
import org.raml.model.Action
import org.raml.model.Raml
import org.raml.model.Resource
import org.raml.parser.loader.ResourceLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.lang.reflect.Array

class EndpointValidator {

  private static final Logger log = LoggerFactory.getLogger(EndpointValidator.class)
  Raml raml

  String serviceName
  String path
  List params

  Resource resource
  ResourceLoader loader
  Map<String, Action> actions
  JsonParser jsonParser = new JsonParser()

  EndpointValidator(ResourceLoader loader, Raml raml, String path, Resource resource, List params, Map<String, Action> actions) {
    this.raml = raml
    this.path = path
    this.params = params
    this.resource = resource
    this.loader = loader
    this.actions = actions.collectEntries { k, v ->
      [k.toString(), v]
    }

    setup()
  }

  Map generateExampleResponse(Map request) {
    def action = actions.get(request.method.toUpperCase())
    def ramlResponse = action.getResponses().find { k, v ->
      k.toInteger() < 300
    }

    def statusCode = ramlResponse.key.toInteger()
    def bodyResponse

    def body = ramlResponse.value.body.get("application/json")
    if (body) {
      if (body.example) {
        def resource = "raml/" + body.example.trim()
        def bodyContents = loader.fetchResource(resource)?.getText("UTF-8")

        if (bodyContents) {

          bodyResponse = jsonParser.parse(bodyContents)
        } else {
          bodyResponse = jsonParser.parse(body.example)
        }
      }
    } else {
      bodyResponse = ramlResponse.value.body.find { k, v ->
        true
      }?.value
    }

    def result = [
        body       : bodyResponse,
        statusCode : statusCode,
        contentType: 'application/json'
    ]

    result
  }

  Map handleResponse(Boolean strictMode, Map request, String response, Boolean error) {
    Action action = actions.get(request.method.toUpperCase())
    Integer statusCode

    def ramlResponse = action.getResponses().find { k, v ->
      k.toInteger() < 300
    }

    if (error) {
      statusCode = 500
    } else if (ramlResponse.value.hasBody() && response == null) {
      statusCode = 404
    } else {
      statusCode = ramlResponse.key.toInteger()
    }


    def result = [
        body      : response,
        statusCode: statusCode
    ]


    if (ramlResponse.value.hasBody()) {
      if (request.headers.get("accept")) {
        def bestMatch = MIMEParse.bestMatch(ramlResponse.value.body.keySet(), request.headers.get("accept")?.first())
        result.contentType = bestMatch
      } else {
        def bestMatch = ramlResponse.value.body.keySet().toList().first()
        result.contentType = bestMatch
      }

      if (strictMode && result.contentType.startsWith("application/json")) {
        def mimeType = ramlResponse.value.body.get(result.contentType)
        Gson gson = new Gson()
        def stringBody = gson.toJson(result.body)
        if (!mimeType.schema) {
          throw new RamlResponseValidationException("The generated response is invalid",
              request.serviceName,
              request.method,
              500,
              result.contentType,
              stringBody,
              RamlResponseValidationException.ErrorReason.SCHEMA_NOT_DEFINED,
          )
        }

        def schemaFormat = JsonLoader.from    String(raml.consolidatedSchemas.get(mimeType.schema))
        def factory = JsonSchemaFactory.defaultFactory()

        def schema = factory.fromSchema(schemaFormat)
        def jsonBody = JsonLoader.fromString(stringBody)
        def report = schema.validate(jsonBody)

        if (!report.isSuccess()) {
          throw new RamlResponseValidationException("The generated response is invalid",
              request.serviceName,
              request.method,
              500,
              result.contentType,
              stringBody,
              RamlResponseValidationException.ErrorReason.INVALID_RESPONSE_BODY,
              report)
        }

      }
    }

    result
  }

  Map handleRequest(HttpServletRequest request) {
    if (!supportsMethod(request.method)) {
      throw new RamlRequestException("Method ${request.method} for endpoint ${resource} does not exist", request.forwardURI, request.method)
    }

    def queryParams = [:]
    def action = actions.get(request.method.toUpperCase())
    def jsonBody
    def bestMatch

    if (request.queryString) {
      queryParams = request.getParameterMap()

      log.debug "action.queryParameters ${action.queryParameters}"
      log.debug "action.is ${action.is}"
      queryParams = action.queryParameters.collectEntries { k, v ->
        [k, validateParam(queryParams.get(k), v)]
      }
    }

    log.info "queryParams $queryParams"
    if (action.hasBody()) {
      bestMatch = MIMEParse.bestMatch(action.body.keySet(), request.getHeader("Accept"))
      def mimeType

      if (bestMatch) {
        mimeType = action.body.get(bestMatch)
      } else {
        throw new RamlRequestException("Unable to find a matching mime type for ${path}", path, request.method)
      }

      if (mimeType.schema) {
        def schemaFormat = JsonLoader.fromString(raml.consolidatedSchemas.get(mimeType.schema))
        def factory = JsonSchemaFactory.defaultFactory()

        def schema = factory.fromSchema(schemaFormat)

        def stringBody = request.toString()
        jsonBody = JsonLoader.fromString(stringBody)
        def report = schema.validate(jsonBody)

        if (!report.isSuccess()) {
          throw new RamlRequestException("Invalid body ${stringBody} for resource ${path} method ${request.method}",
              path,
              request.method,
              report,
              stringBody)
        }
      } else {
        Gson gson = new Gson()
        def stringBody = gson.toJson(request)
        jsonBody = JsonLoader.fromString(stringBody)
      }
    }

    log.info "request.headerNames ${request.headerNames.toList()}"
    log.info "action.headers ${action.headers}"
    def headerValues = request.headerNames.toList().collectEntries {
      [it, request.getHeaders(it).toList()]
    }
    //TODO what if the header is a list ?
    def headers = action.headers.collectEntries { k, v ->
      def headerValue = validateParam(headerValues.get(k)?.first(), v)
      [k, headerValue]
    }
    log.info "result.header ${headers}"

    headers.put('accept', request.getHeaders("accept").toList())

    def result = [
        hasBody    : action.hasBody(),
        serviceName: serviceName,
        jsonBody   : jsonBody,
        params     : params,
        method     : request.method,
        headers    : headers,
        queryParams: queryParams
    ]

    result
  }

  private boolean supportsMethod(String method) {
    method = method.toUpperCase()

    actions.containsKey(method)
  }

  private def setup() {
    if (!resource.displayName) {
      throw new IllegalArgumentException("Resource ${path} has no display name defined")
    }

    def firstChar = "${resource.displayName.charAt(0)}".toLowerCase()

    serviceName = WordUtils
        .capitalizeFully(resource.displayName)
        .replaceAll(" ", "")
        .replaceFirst(".", firstChar)
    serviceName = serviceName + "Service"
  }

  private def validateParam(def value, def valueDefinition) {
    if (!value) {
      value = valueDefinition.defaultValue
    }
    value
  }

}

