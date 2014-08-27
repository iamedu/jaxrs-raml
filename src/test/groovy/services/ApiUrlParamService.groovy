package services

import javax.ws.rs.PathParam

/**
 * Created by tomas on 8/25/14.
 */
public interface ApiUrlParamService {

  String get(@PathParam("songId") String search)

}