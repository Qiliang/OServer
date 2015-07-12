package com.idea.ohmydata.persistence;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.core.uri.UriInfoImpl;


public interface PersistenceDataService {

    EntityCollection readEntityCollection(UriInfo uriInfo) throws ODataApplicationException;

    int countEntityCollection(UriInfo uriInfo) throws ODataApplicationException;

    Entity readEntity(UriInfo uriInfo, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException;

    Entity createEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException, DeserializerException, SerializerException;

    void updateEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException;

    void deleteEntity(UriInfo uriInfo) throws ODataApplicationException;

}
