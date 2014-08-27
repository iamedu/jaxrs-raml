package iamedu.raml.validator

import iamedu.raml.RamlHandlerService
import iamedu.raml.validator.ApiValidator
import iamedu.raml.validator.EndpointValidator
import spock.lang.Ignore
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Created by tomas on 8/22/14.
 */

class ApiValidatorTest extends Specification {
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

  void "HandleRequest"() {

    when:
      RamlHandlerService ramlHandlerService = new RamlHandlerService(ramlDefinition: "raml/jukebox-api.raml", reloadRaml: true)
      ApiValidator av = ramlHandlerService.buildValidator()
      def (EndpointValidator validator, List params) = av.handleResource("/sample-api/api/songs")
      def result = validator.handleRequest(request)
    then:
      validator != null
      params.size() == 0
      result.serviceName == "songsService"
      result.queryParams.get("query") != null

  }

  void "HandleRequest URI parameter"() {

    when:
      RamlHandlerService ramlHandlerService = new RamlHandlerService(ramlDefinition: "raml/jukebox-api.raml", reloadRaml: true)
      ApiValidator av = ramlHandlerService.buildValidator()
      def (EndpointValidator validator, List params) = av.handleResource("/sample-api/api/songs/10")
      def result = validator.handleRequest(request)
    then:
      validator != null
      params.size() == 1
      result.serviceName == "songService"
      result.params.size() == 1

  }

}
