package com.idea.ohmydata;

import com.nikoyo.otest.persisitence.Storage;
import com.nikoyo.otest.persisitence.visitor.FilterVisitor;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpMethod;
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
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.core.uri.queryoption.ExpandItemImpl;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;


@Service
public class DefaultEntityProcessor implements EntityProcessor {

    private OData odata;

    private ServiceMetadata serviceMetadata;

    @Autowired
    private Storage storage;


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
        List<UriParameter> keyPredicates = UriInfoUtils.getKeyPredicates(uriInfo);

        Entity entity = storage.readEntityData(edmEntitySet, keyPredicates, responseExpandOption);

        EdmEntityType entityType = edmEntitySet.getEntityType();

        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).suffix(ContextURL.Suffix.ENTITY).build();

        EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).select(selectOption).expand(responseExpandOption).build();

        ODataFormat oDataFormat = ODataFormat.fromContentType(responseFormat);
        ODataSerializer serializer = this.odata.createSerializer(oDataFormat);
        SerializerResult result = serializer.entity(serviceMetadata, entityType, entity, options);


        response.setContent(result.getContent());
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }

    public void createEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, DeserializerException, SerializerException {

        EdmEntitySet edmEntitySet = UriInfoUtils.getEdmEntitySet(uriInfo);
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();

        InputStream requestInputStream = request.getBody();
        ODataFormat requestODataFormat = ODataFormat.fromContentType(requestFormat);
        ODataDeserializer deserializer = this.odata.createDeserializer(requestODataFormat);
        DeserializerResult result = deserializer.entity(requestInputStream, edmEntityType);
        Entity requestEntity = result.getEntity();

        validateProperties(edmEntityType, requestEntity);
        Entity createdEntity = requestEntity;
        //写入数据
        storage.createEntityData(edmEntitySet, createdEntity);

        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).build();
        EntitySerializerOptions options = EntitySerializerOptions.with().contextURL(contextUrl).build();

        ODataFormat oDataFormat = ODataFormat.fromContentType(responseFormat);
        ODataSerializer serializer = this.odata.createSerializer(oDataFormat);

        SerializerResult serializedResponse = serializer.entity(serviceMetadata, edmEntityType, createdEntity, options);

        response.setContent(serializedResponse.getContent());
        response.setStatusCode(HttpStatusCode.CREATED.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }


    private void validateProperties(EdmEntityType edmEntityType, Entity entity) throws DeserializerException {
        for (String propertyName : edmEntityType.getPropertyNames()) {
            EdmProperty edmProperty = (EdmProperty) edmEntityType.getProperty(propertyName);
            Property property = entity.getProperty(propertyName);
            if ((property == null || property.getValue() == null) && !edmProperty.isNullable()) {
                throw new DeserializerException("Property: " + propertyName + " must not be null.", DeserializerException.MessageKeys.INVALID_NULL_PROPERTY, propertyName);
            }
        }
    }

    public void updateEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, DeserializerException, SerializerException {
        EdmEntitySet edmEntitySet = UriInfoUtils.getEdmEntitySet(uriInfo);
        List<UriParameter> keyPredicates = UriInfoUtils.getKeyPredicates(uriInfo);
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();


        InputStream requestInputStream = request.getBody();
        ODataFormat requestODataFormat = ODataFormat.fromContentType(requestFormat);
        ODataDeserializer deserializer = this.odata.createDeserializer(requestODataFormat);
        DeserializerResult result = deserializer.entity(requestInputStream, edmEntityType);
        Entity requestEntity = result.getEntity();

        HttpMethod httpMethod = request.getMethod();
        storage.updateEntity(edmEntitySet, keyPredicates, requestEntity, httpMethod);

        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());

    }

    public void deleteEntity(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException {
        EdmEntitySet edmEntitySet = UriInfoUtils.getEdmEntitySet(uriInfo);
        List<UriParameter> keyPredicates = UriInfoUtils.getKeyPredicates(uriInfo);
        storage.deleteEntityData(edmEntitySet, keyPredicates);

        response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
    }


}
