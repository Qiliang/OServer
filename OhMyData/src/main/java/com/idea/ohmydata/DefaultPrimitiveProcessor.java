package com.idea.ohmydata;

import com.idea.ohmydata.persistence.Storage;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.processor.PrimitiveCollectionProcessor;
import org.apache.olingo.server.api.processor.PrimitiveProcessor;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.PrimitiveSerializerOptions;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class DefaultPrimitiveProcessor implements PrimitiveProcessor, PrimitiveCollectionProcessor {

    private OData odata;
    private Storage storage;

    public DefaultPrimitiveProcessor(Storage storage) {
        this.storage = storage;
    }

    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
    }

    public void readPrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        read(response, uriInfo, responseFormat, false);
    }

    private void read(ODataResponse response, UriInfo uriInfo, ContentType responseFormat, boolean isCollection) throws ODataApplicationException, SerializerException {

//        EdmEntitySet edmEntitySet = UriInfoUtils.getEntitySet(uriInfo);
////        List<UriParameter> keyPredicates = UriInfoUtils.getKeyPredicates(uriInfo);
////        EdmProperty edmProperty = UriInfoUtils.EdmProperty(uriInfo);
////        String edmPropertyName = edmProperty.getName();
//        EdmPrimitiveType edmPropertyType = (EdmPrimitiveType) edmProperty.getType();
////
//
//        Entity entity = storage.readEntityData(edmEntitySet, keyPredicates, new ExpandOptionImpl());
//        if (entity == null)
//            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
//
//
//        Property property = entity.getProperty(edmPropertyName);
//        if (property == null)
//            throw new ODataApplicationException("Property not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
//
//
//        Object value = property.getValue();
//        if (value == null)
//            response.setStatusCode(HttpStatusCode.NO_CONTENT.getStatusCode());
//
//
//        ODataFormat format = ODataFormat.fromContentType(responseFormat);
//        ODataSerializer serializer = odata.createSerializer(format);
//
//        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).navOrPropertyPath(edmPropertyName).build();
//        PrimitiveSerializerOptions options = PrimitiveSerializerOptions.with().contextURL(contextUrl).build();
//        SerializerResult serializerResult = isCollection
//                ? serializer.primitiveCollection(edmPropertyType, property, options)
//                : serializer.primitive(edmPropertyType, property, options);
//
//        InputStream propertyStream = serializerResult.getContent();
//        response.setContent(propertyStream);
//        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
//        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());

    }

    public void updatePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat)
            throws ODataApplicationException, DeserializerException, SerializerException {
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }

    public void deletePrimitive(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException {
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }

    @Override
    public void readPrimitiveCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, SerializerException {
        read(response, uriInfo, responseFormat, true);
    }

    @Override
    public void updatePrimitiveCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType requestFormat, ContentType responseFormat) throws ODataApplicationException, DeserializerException, SerializerException {
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }

    @Override
    public void deletePrimitiveCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException {
        throw new ODataApplicationException("Not supported.", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ROOT);
    }
}
