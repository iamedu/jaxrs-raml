package iamedu.raml

import com.google.gson.Gson
import com.google.gson.JsonParser
import groovy.transform.Canonical
import iamedu.raml.exception.RamlResponseValidationException
import iamedu.raml.exception.handlers.RamlResponseValidationExceptionHandler
import iamedu.raml.validator.EndpointValidator
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.util.WebUtils

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.ws.rs.HeaderParam
import javax.ws.rs.PathParam
import javax.ws.rs.QueryParam

/**
 * Created by atomsfat on 8/20/14.
 */
@Canonical
class RamlApiHandler {

  private static final Logger log = LoggerFactory.getLogger(RamlApiHandler.class)

  ApplicationContext appContext
  RamlHandlerService ramlHandlerService

  Boolean serveExamples = true
  Boolean strictMode = false


  ResponseEntity<String> handle(HttpServletRequest request, HttpServletResponse response) {

    def validator = ramlHandlerService.buildValidator()
    def (EndpointValidator endpointValidator, paramValues) = validator.handleResource(getForwardURI(request))

    Map req = endpointValidator.handleRequest(request)


    def service
    def result
    Boolean error = false
    log.info "We got service ${req.serviceName} ? ${appContext.containsBean(req.serviceName)}"
    if (appContext.containsBean(req.serviceName)) {
      service = appContext.getBean(req.serviceName)
    }

    if (service) {
      result = handleService(service, req, paramValues)
    } else {
      if (!serveExamples) {
        throw new RuntimeException("No service name ${req.serviceName} exists")
      }
    }

    try {
      result = endpointValidator.handleResponse(strictMode, req, result, error)
    } catch (RamlResponseValidationException ex) {
      def beans = appContext.getBeansOfType(RamlResponseValidationExceptionHandler.class)
      beans.each { RamlResponseValidationExceptionHandler it ->
        it.handleResponseValidationException(ex)
      }

      if (strictMode) {
        throw ex
      }
    }

    if ((result == null || result.body == null) && serveExamples) {
      result = endpointValidator.generateExampleResponse(req)
    }

    HttpHeaders responseHeaders = new HttpHeaders();

    if (result.contentType?.startsWith("application/json")) {
      responseHeaders.set("Content-Type", result.contentType);
      Gson gson = new Gson()
      return new ResponseEntity<String>(gson.toJson(result.body), responseHeaders, HttpStatus.valueOf(result.statusCode))
    } else {
      responseHeaders.set("Content-Type", result.contentType);
      return new ResponseEntity<String>(result.body, HttpStatus.valueOf(result.statusCode))
    }

  }

  private handleService(Object service, Map req, def paramValues) {

    def result
    def methodName = req.method.toLowerCase()
    def methods = service.class.getMethods().grep {
      it.name == methodName
    }
    if (methods.size() == 1) {
      def method = methods.first()

      def params = [req.params, paramValues].transpose().collectEntries {
        def (key, definition) = it[0]
        def paramValue = it[1]
        [key, [definition: definition, value: paramValue]]
      }

      def headers = req.headers.each { k, v ->
        [k, v]
      }
      log.info("Ready to invoke ${req.serviceName} dataAvailable -> [params : ${params} headers: ${headers}, req: ${req}]")
      if (method.parameterTypes.size() == 0) {
        result = method.invoke(service)
      } else {
        def invokeParams = []
        method.parameterTypes.eachWithIndex { it, i ->
          def param
          def headerAnnotation = method.parameterAnnotations[i].find {
            it.annotationType() == HeaderParam
          }
          def paramAnnotation = method.parameterAnnotations[i].find {
            it.annotationType() == PathParam
          }
          def queryAnnotation = method.parameterAnnotations[i].find {
            it.annotationType() == QueryParam
          }
          if (paramAnnotation) {
            def parameterName = paramAnnotation.value()
            def paramValue = params[parameterName]
            param = paramValue.value.asType(it)
          } else if (queryAnnotation) {
            def parameterName = queryAnnotation.value()
            def paramValue = req.queryParams[parameterName]
            param = paramValue.asType(it)
          } else if (headerAnnotation) {
            def parameterName = headerAnnotation.value()
            def paramValue = headers[parameterName]
            param = paramValue.asType(it)
          } else if (Map.isAssignableFrom(it)) {
            JsonParser jsonParser = new JsonParser()
            param = jsonParser.parse(req.jsonBody.toString())
          }
          invokeParams.push(param)
          log.info "invokeParams $param"
        }
        result = method.invoke(service, invokeParams as Object[])
      }
    } else if (methods.size() > 1) {
      throw new RuntimeException("Only one method can be named ${methodName} in service ${req.serviceName}")
    } else {
      if (!serveExamples) {
        throw new RuntimeException("No method named ${methodName} in service ${req.serviceName}")
      }
    }

    return result
  }

  public static String getForwardURI(HttpServletRequest request) {
    String result = (String) request.getAttribute(WebUtils.FORWARD_REQUEST_URI_ATTRIBUTE);
    if (StringUtils.isBlank(result)) result = request.getRequestURI();
    return result;
  }
}
