package services

import javax.ws.rs.QueryParam

/**
 * Created by tomas on 8/21/14.
 */
interface ApiQueryParamService {
  String get(@QueryParam("query") String query)
}

