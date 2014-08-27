package iamedu.raml.validator

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import iamedu.raml.exception.RamlRequestException
import org.raml.model.Action
import org.raml.model.ActionType
import org.raml.model.Raml
import org.raml.model.Resource
import org.raml.parser.loader.ResourceLoader

import java.util.regex.Pattern

class ApiValidator {

  String basePath

  Integer maxCacheCapacity = 5000
  Raml raml
  ResourceLoader loader
  Map<Pattern, EndpointValidator> endpoints
  Map entryCache

  ApiValidator(Raml raml, ResourceLoader loader) {
    this.raml = raml
    this.loader = loader
    entryCache = new ConcurrentLinkedHashMap.Builder()
      .maximumWeightedCapacity(maxCacheCapacity)
      .build()
    setupValidator()
  }

  List handleResource(String resource) {

    Map.Entry entry = null
    Boolean reloadRaml = true

    entry = entryCache.get(resource)
    if(!entry) {
      entry = endpoints.find {Pattern endpoint, EndpointValidator validator ->
        def matcher = resource =~ endpoint
        matcher.matches()
      }
      if(!reloadRaml) {
        entryCache.put(resource, entry)
      }
    }

    if(entry) {
      List params = []
      def matcher = resource =~ entry.key
      if(matcher[0] instanceof java.util.List) {
        params = matcher[0].drop(1).collect { it }
      }
      return [entry.value, params]
    } else {
      throw new RamlRequestException("Endpoint ${resource} does not exist", resource)
    }
  }

  private void processEndpoint(String resourcePath, Resource resource, Map<ActionType, Action> actions) {
    def replacePartPattern = "([^/]*)"
    def partPattern = /\{[^\{\}]*\}/

    def regexPath = resourcePath.replaceAll(partPattern, replacePartPattern)
    def params = (resourcePath =~ partPattern).collect { it }.collect {
      def k = it.replaceAll(/\{|\}/, "")
      [k, resource.uriParameters.get(k)]
    }

    Pattern pattern = Pattern.compile(basePath + regexPath)
    
    endpoints.put(pattern, new EndpointValidator(loader, raml, resourcePath, resource, params, actions))
  }

  private void processEndpoints(String prefix, Map resources) {
    resources.each { key, Resource resource ->
      def currentPrefix  = "${prefix}${key}"
      if(resource.actions.size() > 0) {
        processEndpoint(currentPrefix.toString(), resource, resource.actions)
      }
      processEndpoints(currentPrefix.toString(), resource.resources)
    }
  }

  private void setupValidator() {
    endpoints = new HashMap()
    basePath = raml.basePath
    if(basePath.endsWith("/")) {
      basePath = basePath.substring(0, basePath.length() - 1)
    }
    processEndpoints("", raml.resources)
  }

}
