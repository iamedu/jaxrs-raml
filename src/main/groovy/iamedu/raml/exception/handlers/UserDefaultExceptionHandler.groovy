package iamedu.raml.exception.handlers

class UserDefaultExceptionHandler implements UserExceptionHandler<Exception> {

  Map handleException(Exception exception) {
   //TODO getCause
    def cause

    def responseBody = [
      errorCode: "unhandledError",
      exception: [
        message: exception.message,
        class: exception.class.name
      ]
    ]
    if(exception != cause) {
      responseBody.cause = [
        message: cause.message,
        class: cause.class.name
      ]
    }

    responseBody
  }

}

