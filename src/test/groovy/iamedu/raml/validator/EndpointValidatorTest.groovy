package iamedu.raml.validator

import iamedu.raml.RamlHandlerService
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Created by tomas on 8/25/14.
 */
class EndpointValidatorTest extends Specification {

  HttpServletRequest request
  HttpServletResponse response

  def setup() {
    request = Mock(HttpServletRequest)
    response = Mock(HttpServletResponse)
    request.getMethod() >> { "get" }
    request.getHeaderNames() >> {
      return new Vector<String>().elements()
    }
    request.getHeaders("accept") >> {
      Vector<String> h = new Vector<String>()
      h.add('application/json')
      h.elements()
    }
    request.getQueryString() >> { "query=metro" }
    request.getParameterMap() >> {
      Map<String, String[]> params = new HashMap<String, String[]>()
      params.put("query", ["metro"])
      return params
    }
  }

  def "GenerateExampleResponse"() {
    when:
      RamlHandlerService ramlHandlerService = new RamlHandlerService(ramlDefinition: "raml/jukebox-api.raml", reloadRaml: true)
      ApiValidator av = ramlHandlerService.buildValidator()
      def (EndpointValidator validator, params) = av.handleResource("/sample-api/api/songs")
      Map req = validator.handleRequest(request)
      def example = validator.generateExampleResponse(req)
      println "GenerateExampleResponse $example"
    then:
      example != null
      example.contentType == "application/json"
      example.body
      example.statusCode == 200


  }

  def "HandleResponse"() {
    when:
      RamlHandlerService ramlHandlerService = new RamlHandlerService(ramlDefinition: "raml/jukebox-api.raml", reloadRaml: true)
      ApiValidator av = ramlHandlerService.buildValidator()
      def (EndpointValidator validator, params) = av.handleResource("/sample-api/api/songs")
      Map req = validator.handleRequest(request)
      def res = validator.handleResponse(false, req, "", false)
      println "HandleResponse $res"
    then:
      res.statusCode == 200
      res.contentType == "application/json"


  }

  def "HandleRequest"() {
    when:
      RamlHandlerService ramlHandlerService = new RamlHandlerService(ramlDefinition: "raml/jukebox-api.raml", reloadRaml: true)
      ApiValidator av = ramlHandlerService.buildValidator()
      def (EndpointValidator validator, params) = av.handleResource("/sample-api/api/songs")
      def req = validator.handleRequest(request)
      println "HandleRequest $req"
    then:
      req != null


  }

  def "SupportsMethod"() {
    when:
      RamlHandlerService ramlHandlerService = new RamlHandlerService(ramlDefinition: "raml/jukebox-api.raml", reloadRaml: true)
      ApiValidator av = ramlHandlerService.buildValidator()
      def (EndpointValidator validator, params) = av.handleResource("/sample-api/api/songs")
    then:
      validator.supportsMethod("get") == true
  }
}
