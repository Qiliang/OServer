package com.idea.ohmydata;

import com.idea.ohmydata.persistence.JsonObj;
import com.idea.ohmydata.persistence.PersistenceDataService;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.processor.EntityProcessor;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;


@Service
public class DefaultEntityProcessor implements EntityProcessor {

    private OData odata;

    private ServiceMetadata serviceMetadata;

    @Autowired
    private PersistenceDataService persistenceDataService;

    public DefaultEntityProcessor() {

    }

    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    public void readEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, SerializerException {

        ExpandOption responseExpandOption = UriInfoUtils.getExpand(uriInfo);
        EdmEntitySet edmEntitySet = UriInfoUtils.getEdmEntitySet(uriInfo);
        SelectOption selectOption = UriInfoUtils.getSelect(uriInfo);

        JsonObj entityObj = persistenceDataService.readEntity(uriInfo, odata, serviceMetadata);
        try {
            ODataDeserializer deserializer = this.odata.createDeserializer(ODataFormat.JSON);
            Entity respEntity = deserializer.entity(entityObj.toInputStream(), entityObj.getType()).getEntity();
            respEntity.setType(entityObj.getType().getFullQualifiedName().getFullQualifiedNameAsString());

            EdmEntityType entityType = entityObj.getType();
            ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).suffix(ContextURL.Suffix.ENTITY).build();
            EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).select(selectOption).expand(responseExpandOption).build();

            ODataFormat oDataFormat = ODataFormat.fromContentType(responseFormat);
            ODataSerializer serializer = this.odata.createSerializer(oDataFormat);
            SerializerResult result = serializer.entity(serviceMetadata, entityType, respEntity, options);


            response.setContent(result.getContent());
            response.setStatusCode(HttpStatusCode.OK.getStatusCode());
            response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
        } catch (Exception e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault(), e);
        }
    }

    public void createEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, DeserializerException, SerializerException {

        EdmEntitySet edmEntitySet = UriInfoUtils.getEdmEntitySet(uriInfo);

        //写入数据
        JsonObj entityObj = persistenceDataService.createEntity(uriInfo, request, odata, serviceMetadata);

        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();
        EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).build();

        ODataFormat oDataFormat = ODataFormat.fromContentType(responseFormat);
        ODataSerializer serializer = this.odata.createSerializer(oDataFormat);
        SerializerResult serializedResponse = serializer.entity(serviceMetadata, entityObj.getType(), entityObj.toEntity(odata), options);

        response.setContent(serializedResponse.getContent());
        response.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, DeserializerException, SerializerException {

        persistenceDataService.updateEntity(uriInfo, request, odata, serviceMetadata);
        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());

    }

    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException {
        persistenceDataService.deleteEntity(uriInfo);
        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }


}
