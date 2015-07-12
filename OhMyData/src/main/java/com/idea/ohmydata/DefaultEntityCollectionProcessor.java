package com.idea.ohmydata;

import com.idea.ohmydata.persistence.PersistenceDataService;
import com.idea.ohmydata.persistence.Storage;
import org.apache.commons.io.IOUtils;
import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.processor.CountEntityCollectionProcessor;
import org.apache.olingo.server.api.processor.EntityCollectionProcessor;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;

@Service
public class DefaultEntityCollectionProcessor implements EntityCollectionProcessor, CountEntityCollectionProcessor {

    private OData odata;
    private ServiceMetadata serviceMetadata;

    @Autowired
    private PersistenceDataService persistenceDataService;

    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
        this.serviceMetadata = serviceMetadata;
    }

    public void readEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo, ContentType responseFormat) throws ODataApplicationException, SerializerException {


        uriInfo.asUriInfoResource().getTopOption();

        String sql = UriInfoUtils.getFilter(uriInfo);
        String orderBy = UriInfoUtils.getOrderBy(uriInfo);
        ExpandOption responseExpandOption = UriInfoUtils.getExpand(uriInfo);
        int top = UriInfoUtils.getTop(uriInfo);
        int skip = UriInfoUtils.getSkip(uriInfo);
        EdmEntitySet edmEntitySet = UriInfoUtils.getEdmEntitySet(uriInfo);
        SelectOption selectOption = UriInfoUtils.getSelect(uriInfo);

        EntityCollection entitySet = persistenceDataService.readEntityCollection(uriInfo);
//        EntityCollection entitySet = storage.readEntitySetData(edmEntitySet, top, skip, sql, orderBy, responseExpandOption);

        ODataFormat format = ODataFormat.fromContentType(responseFormat);
        ODataSerializer serializer = odata.createSerializer(format);


        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        ContextURL contextUrl = ContextURL.with().entitySet(edmEntitySet).serviceRoot(URI.create(request.getRawBaseUri() + "/")).build();

        EntityCollectionSerializerOptions opts = EntityCollectionSerializerOptions.with()
                .contextURL(contextUrl)
                .expand(responseExpandOption)
                .select(selectOption)
                .build();
        SerializerResult serializedContent = serializer.entityCollection(serviceMetadata, edmEntityType, entitySet, opts);

        response.setContent(serializedContent.getContent());
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
        response.setHeader(HttpHeader.CONTENT_TYPE, responseFormat.toContentTypeString());
    }


    @Override
    public void countEntityCollection(ODataRequest request, ODataResponse response, UriInfo uriInfo) throws ODataApplicationException, SerializerException {
        String sql = UriInfoUtils.getFilter(uriInfo);
        EdmEntitySet edmEntitySet = UriInfoUtils.getEdmEntitySet(uriInfo);

        int count = persistenceDataService.countEntityCollection(uriInfo);
        response.setContent(IOUtils.toInputStream(String.valueOf(count)));
        response.setStatusCode(HttpStatusCode.OK.getStatusCode());
    }
}
