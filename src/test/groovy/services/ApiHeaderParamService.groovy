package services

import javax.ws.rs.HeaderParam

/**
 * Created by tomas on 8/26/14.
 */
public interface ApiHeaderParamService {
  String get(@HeaderParam("X-Auth-Token") String token)
}