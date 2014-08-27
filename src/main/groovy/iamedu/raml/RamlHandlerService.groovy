package iamedu.raml

import groovy.transform.Canonical
import iamedu.raml.validator.ApiValidator
import iamedu.raml.validator.ApiValidatorBuilder

@Canonical
class RamlHandlerService {

  ApiValidator validator

  String ramlDefinition
  Boolean reloadRaml


  private ApiValidator doBuildValidator(def ramlDefinition) {
    if(!ramlDefinition) {
      throw new RuntimeException("Raml definition is not set")
    }
    ApiValidatorBuilder builder = new ApiValidatorBuilder()
    builder.setRamlLocation(ramlDefinition)

    builder.build()
  }

  ApiValidator buildValidator() {

    if(!validator || reloadRaml) {
      validator = doBuildValidator(ramlDefinition)
    }

    validator
  }

}
