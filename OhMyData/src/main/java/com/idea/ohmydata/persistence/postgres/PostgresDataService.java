package com.idea.ohmydata.persistence.postgres;

import com.idea.ohmydata.ODataServlet;
import com.idea.ohmydata.UriInfoUtils;
import com.idea.ohmydata.persistence.PersistenceDataService;
import com.idea.ohmydata.persistence.PersistenceJsonSerializer;
import org.apache.commons.io.IOUtils;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.serializer.EntitySerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.apache.olingo.server.api.uri.*;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
class PostgresDataService implements PersistenceDataService {


    @Autowired
    private ODataServlet servlet;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    PersistenceJsonSerializer serializer = new PersistenceJsonSerializer();

    @Override
    public EntityCollection readEntityCollection(UriInfo uriInfo) throws ODataApplicationException {
        return null;
    }

    @Override
    public int countEntityCollection(UriInfo uriInfo) throws ODataApplicationException {
        return 0;
    }

    @Override
    public Entity readEntity(UriInfo uriInfo, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
        List<UriParameter> keyPredicates = UriInfoUtils.getKeyPredicates(uriInfo);
        List<UriResource> uriResources = uriInfoResource.getUriResourceParts();
        UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResources.get(uriResources.size() - 1);

        return getEntity(uriResourceEntitySet.getEntitySet(), keyPredicates, odata);
    }

    private void where(StringBuffer sqlBuilder, EdmEntitySet edmEntitySet, List<UriParameter> keyParams) {
        sqlBuilder.append(" where \"repositoryId\" = '").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        for (UriParameter keyParam : keyParams) {
            sqlBuilder.append(" and data->>'").append(keyParam.getName()).append("' =  ").append(keyParam.getText());
        }
    }

    private Entity getEntity(EdmEntitySet edmEntitySet, List<UriParameter> keyPredicates, OData odata) throws ODataApplicationException {

        StringBuffer sqlBuilder = new StringBuffer("select * from entity ");
        where(sqlBuilder, edmEntitySet, keyPredicates);
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sqlBuilder.toString());
        if (result.size() == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        Map row = result.get(0);


        DeserializerResult deserializerResult;
        try {
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            PGobject jsonObject = (PGobject) row.get("data");
            deserializerResult = deserializer.entity(IOUtils.toInputStream(jsonObject.getValue()), edmEntitySet.getEntityType());
        } catch (DeserializerException e) {
            throw new ODataApplicationException("DeserializerException:" + e.getMessage(), HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        }

        return deserializerResult.getEntity();
    }


    @Override
    public Entity createEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        InputStream jsonStream = null;
        try {
            UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
            List<UriResource> uriResources = uriInfoResource.getUriResourceParts();

            UriInfoKeys uriInfoKeys = getUriInfoKeys(odata, serviceMetadata, uriResources);

            Entity refEntity = uriInfoKeys.getRefEntity();
            EdmEntitySet edmEntitySet = uriInfoKeys.getEdmEntitySet();

            InputStream requestInputStream = request.getBody();
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            DeserializerResult result = deserializer.entity(requestInputStream, edmEntitySet.getEntityType());
            Entity requestEntity = result.getEntity();
            jsonStream = serializer.entity(serviceMetadata, edmEntitySet.getEntityType(), requestEntity, EntitySerializerOptions.with().build());

            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(IOUtils.toString(jsonStream));
            jdbcTemplate.update("INSERT INTO entity(\"repositoryId\", \"name\", \"data\" ) VALUES (?, ?, ?)", servlet.getRepositoryId(), edmEntitySet.getName(), jsonObject);


            if (refEntity != null) {
                String refKeyPredicates = serializer.getKeyPredicates(edmEntitySet.getEntityType(), requestEntity);


                updateEntity(edmEntitySet, null, refEntity, serviceMetadata);
            }

            return requestEntity;
        } catch (DeserializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
        } catch (SerializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (IOException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (SQLException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } finally {
            IOUtils.closeQuietly(jsonStream);
        }
    }

    private UriInfoKeys getUriInfoKeys(OData odata, ServiceMetadata serviceMetadata, List<UriResource> uriResources) throws ODataApplicationException {
        UriInfoKeys uriInfoKeys = new UriInfoKeys();
        for (int i = 0; i < uriResources.size(); i++) {
            UriResource uriResource = uriResources.get(i);
            if (uriResource.getKind() == UriResourceKind.entitySet) {
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
                uriInfoKeys.setEdmEntitySet(uriResourceEntitySet.getEntitySet());
                if (uriResourceEntitySet.getKeyPredicates() == null || uriResourceEntitySet.getKeyPredicates().size() == 0) {
                    break;
                }
                uriInfoKeys.setKeyParams(uriResourceEntitySet.getKeyPredicates());
                uriInfoKeys.setRefEntity(getEntity(uriResourceEntitySet.getEntitySet(), uriResourceEntitySet.getKeyPredicates(), odata));
            } else if (uriResource.getKind() == UriResourceKind.navigationProperty) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) uriResource;
                EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                for (EdmNavigationPropertyBinding edmNavigationPropertyBinding : uriInfoKeys.getEdmEntitySet().getNavigationPropertyBindings()) {
                    if (edmNavigationPropertyBinding.getPath().equals(edmNavigationProperty.getName())) {
                        uriInfoKeys.setEdmEntitySet(serviceMetadata.getEdm().getEntityContainer().getEntitySet(edmNavigationPropertyBinding.getTarget()));
                    }
                }
                if (uriResourceNavigation.getKeyPredicates() == null || uriResourceNavigation.getKeyPredicates().size() == 0)
                    break;
                uriInfoKeys.setKeyParams(uriResourceNavigation.getKeyPredicates());
                uriInfoKeys.setRefEntity(getEntity(uriInfoKeys.getEdmEntitySet(), uriResourceNavigation.getKeyPredicates(), odata));
            }

        }
        return uriInfoKeys;
    }


    @Override
    public void updateEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        try {
            UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
            List<UriResource> uriResources = uriInfoResource.getUriResourceParts();
            UriInfoKeys uriInfoKeys = getUriInfoKeys(odata, serviceMetadata, uriResources);

            Entity entity = uriInfoKeys.getRefEntity();
            EdmEntitySet edmEntitySet = uriInfoKeys.getEdmEntitySet();

            InputStream requestInputStream = request.getBody();
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            DeserializerResult result = deserializer.entity(requestInputStream, edmEntitySet.getEntityType());
            Entity updateEntity = result.getEntity();


            for (Property existingProp : entity.getProperties()) {
                String propName = existingProp.getName();

                if (isKey(edmEntitySet.getEntityType(), propName)) {
                    continue;
                }

                Property updateProperty = updateEntity.getProperty(propName);

                if (updateProperty == null) {
                    if (request.getMethod().equals(HttpMethod.PATCH)) {
                        continue;
                    } else if (request.getMethod().equals(HttpMethod.PUT)) {
                        existingProp.setValue(existingProp.getValueType(), null);
                        continue;
                    }
                }

                existingProp.setValue(existingProp.getValueType(), updateProperty.getValue());
            }
            updateEntity(edmEntitySet, uriInfoKeys.getKeyParams(), entity, serviceMetadata);

        } catch (DeserializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
        } catch (SerializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (IOException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (SQLException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } finally {
            // IOUtils.closeQuietly(jsonStream);
        }
    }

    private void updateEntity(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, Entity entity, ServiceMetadata serviceMetadata) throws ODataApplicationException, SerializerException, IOException, SQLException {
        StringBuffer sqlBuilder = new StringBuffer("update entity set data = ? ");
        where(sqlBuilder, edmEntitySet, keyParams);
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        InputStream jsonStream = null;
        try {
            jsonStream = serializer.entity(serviceMetadata, edmEntitySet.getEntityType(), entity, EntitySerializerOptions.with().build());
            jsonObject.setValue(IOUtils.toString(jsonStream));
        } finally {
            IOUtils.closeQuietly(jsonStream);
        }

        int result = jdbcTemplate.update(sqlBuilder.toString(), jsonObject);
        if (result == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
    }

    private boolean isKey(EdmEntityType edmEntityType, String propertyName) {
        List<EdmKeyPropertyRef> keyPropertyRefs = edmEntityType.getKeyPropertyRefs();
        for (EdmKeyPropertyRef propRef : keyPropertyRefs) {
            String keyPropertyName = propRef.getName();
            if (keyPropertyName.equals(propertyName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void deleteEntity(UriInfo uriInfo) throws ODataApplicationException {

    }

    class UriInfoKeys {
        Entity refEntity = null;
        EdmEntitySet edmEntitySet = null;
        List<UriParameter> keyParams;

        public Entity getRefEntity() {
            return refEntity;
        }

        public void setRefEntity(Entity refEntity) {
            this.refEntity = refEntity;
        }

        public EdmEntitySet getEdmEntitySet() {
            return edmEntitySet;
        }

        public void setEdmEntitySet(EdmEntitySet edmEntitySet) {
            this.edmEntitySet = edmEntitySet;
        }

        public List<UriParameter> getKeyParams() {
            return keyParams;
        }

        public void setKeyParams(List<UriParameter> keyParams) {
            this.keyParams = keyParams;
        }
    }

}
