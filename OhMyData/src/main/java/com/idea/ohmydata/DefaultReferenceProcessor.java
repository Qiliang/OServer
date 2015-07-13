package com.idea.ohmydata;

import com.idea.ohmydata.persistence.PersistenceDataService;
import com.idea.ohmydata.persistence.Storage;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.processor.ReferenceCollectionProcessor;
import org.apache.olingo.server.api.processor.ReferenceProcessor;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.apache.olingo.server.api.uri.queryoption.SystemQueryOption;
import org.apache.olingo.server.api.uri.queryoption.SystemQueryOptionKind;
import org.apache.olingo.server.core.uri.parser.Parser;
import org.apache.olingo.server.core.uri.parser.UriParserException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.util.*;

@Service
public class DefaultReferenceProcessor implements ReferenceProcessor, ReferenceCollectionProcessor {

    private OData odata;

    private ServiceMetadata serviceMetadata;


    @Autowired
    private PersistenceDataService persistenceDataService;

    @Override
    public void readReference(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }

    @Override
    public void createReference(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat) throws ODataApplicationException, DeserializerException {

        persistenceDataService.createReference(uriInfo, request, odata, serviceMetadata);

        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }

    @Override
    public void updateReference(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat) throws ODataApplicationException, DeserializerException {
        createReference(request, response, uriInfo, requestFormat);
    }

    @Override
    public void deleteReference(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException {

        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);

        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        // storage.updateEntityNavigationLinks(edmEntitySet, uriResourceEntitySet.getKeyPredicates(), uriResourceNavigation.getProperty().getName(), new ArrayList(), HttpMethod.PUT);
        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public void readReferenceCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();

        Collection<SystemQueryOption> systemQueryOptions = uriInfo.getSystemQueryOptions();
        for (SystemQueryOption systemQueryOption : systemQueryOptions) {
            if (systemQueryOption.getKind().equals(SystemQueryOptionKind.FILTER)) {
                FilterOption filterOption = (FilterOption) systemQueryOption;

            }
        }


        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) resourcePaths.get(0);
        UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) resourcePaths.get(1);
        EdmEntitySet edmEntitySet = uriResourceEntitySet.getEntitySet();
        edmEntitySet.getEntityType();

        // 2nd: fetch the data from backend for this requested EntitySetName
        // it has to be delivered as EntitySet object
        Map entitySetMap = null;//storage.readEntityNavigationLinks(edmEntitySet, uriResourceEntitySet.getKeyPredicates());
        String refKey = uriResourceNavigation.getProperty().getName();
        EntityCollection entitySet = new EntityCollection();
        if (entitySetMap.containsKey(refKey)) {
            List<String> list = (List<String>) entitySetMap.get(refKey);
            for (String id : list) {
                Entity entity = new Entity();
                entity.setId(URI.create(request.getRawBaseUri() + id));
                entitySet.getEntities().add(entity);
            }
        }


        // 3rd: create a serializer based on the requested format (json)
        ODataFormat format = ODataFormat.fromContentType(responseFormat);
        ODataSerializer serializer = odata.createSerializer(format);

        // and serialize the content: transform from the EntitySet object to InputStream
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).serviceRoot(URI.create(request.getRawBaseUri() + "/")).build();
        EntityCollectionSerializerOptions opts = EntityCollectionSerializerOptions.with().contextURL(contextUrl).setWriteOnlyReferences(true).build();
        SerializerResult serializedContent = serializer.entityCollection(serviceMetadata, edmEntityType, entitySet, opts);
        // Finally: configure the response object: set the body, headers and status code
        response.setContent(serializedContent.getContent());
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());

    }
}
