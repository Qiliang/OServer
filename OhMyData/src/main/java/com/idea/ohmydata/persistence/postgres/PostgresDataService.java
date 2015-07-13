package com.idea.ohmydata.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idea.ohmydata.ODataServlet;
import com.idea.ohmydata.UriInfoUtils;
import com.idea.ohmydata.persistence.PersistenceDataService;
import com.idea.ohmydata.persistence.PersistenceJsonSerializer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.core.uri.parser.Parser;
import org.apache.olingo.server.core.uri.parser.UriParserException;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.SQLException;
import java.util.*;

@Service
class PostgresDataService implements PersistenceDataService {


    @Autowired
    private ODataServlet servlet;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    PersistenceJsonSerializer serializer = new PersistenceJsonSerializer();

    @Override
    @Transactional
    public void createReference(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
        List<UriResource> uriResources = uriInfoResource.getUriResourceParts();
        try {
            UriInfoContext uriInfoContext = getUriInfoContext(odata, serviceMetadata, uriResources);
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            DeserializerResult result = deserializer.entityReferences(request.getBody());


            List<Map> propertyJsonList = new ArrayList<>();
            int rawBaseUriIndex = request.getRawBaseUri().length();
            List<List<UriParameter>> uriParameterList = new ArrayList<>();
            for (URI uri : result.getEntityReferences()) {
                UriInfo refUriInfo = new Parser().parseUri(uri.toString().substring(rawBaseUriIndex), null, null, serviceMetadata.getEdm());
                UriInfoContext refContext = getUriInfoContext(odata, serviceMetadata, refUriInfo.getUriResourceParts());
                Map propertyMap = new HashMap<>();
                for (UriParameter uriParameter : refContext.getKeyParams()) {
                    propertyMap.put(uriParameter.getName(), uriParameter.getText().replace("'", StringUtils.EMPTY));
                }
                propertyJsonList.add(propertyMap);

            }

            Map propertyJsonMap = new HashMap<>();
            propertyJsonMap.put(uriInfoContext.getEdmNavigationProperty().getName(), propertyJsonList);
            updateNavigationProperty(uriInfoContext.getRefEdmEntitySet(), uriInfoContext.getKeyParams(), propertyJsonMap);

        } catch (SerializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (SQLException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (UriParserException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
        } catch (DeserializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
        } catch (IOException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } finally {

        }
    }


    @Override
    public EntityCollection readEntityCollection(UriInfo uriInfo, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
        List<UriResource> uriResources = uriInfoResource.getUriResourceParts();

        UriInfoContext uriInfoContext = getUriInfoContext(odata, serviceMetadata, uriResources);
        if (uriInfoContext.getEdmNavigationProperty() != null) {
            EdmNavigationProperty edmNavigationProperty = uriInfoContext.getEdmNavigationProperty();

            Link bindingLink = uriInfoContext.getRefEntity().getNavigationLink(edmNavigationProperty.getName());

            EdmEntitySet refEdmEntitySet = getNavigationPropertyBindingSet(edmNavigationProperty.getName(), uriInfoContext.getRefEdmEntitySet(), serviceMetadata);
            return readEntitySetData(refEdmEntitySet, -1, -1,    whereIds(bindingLink.getInlineEntitySet()), "", new ExpandOptionImpl(), odata, serviceMetadata);
        } else {
            return readEntitySetData(uriInfoContext.getEdmEntitySet(), uriInfoResource, odata, serviceMetadata);
        }
    }

    private EntityCollection readEntitySetData(EdmEntitySet edmEntitySet, UriInfoResource uriInfoResource, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        int top = uriInfoResource.getTopOption() == null ? 0 : uriInfoResource.getTopOption().getValue();
        int skip = uriInfoResource.getSkipOption() == null ? 0 : uriInfoResource.getSkipOption().getValue();
        String sql = "";
        String orderBy = UriInfoUtils.getOrderBy(uriInfoResource.getOrderByOption());
        ExpandOption expandOption = uriInfoResource.getExpandOption() == null ? new ExpandOptionImpl() : uriInfoResource.getExpandOption();
        return readEntitySetData(edmEntitySet, top, skip, sql, orderBy, expandOption, odata, serviceMetadata);

    }

    private EntityCollection readEntitySetData(EdmEntitySet edmEntitySet, int top, int skip, String sql, String orderBy, ExpandOption expandOption, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        StringBuffer sqlBuilder = new StringBuffer();
        sqlBuilder.append("select * from entity where \"repositoryId\" = ").append("'").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        if (StringUtils.isNotBlank(sql)) {
            sqlBuilder.append(" and ( ").append(sql).append(" )");
        }
        sqlBuilder.append(orderBy);
        if (top > 0) sqlBuilder.append(" limit ").append(top);
        if (skip > 0) sqlBuilder.append(" offset ").append(skip);

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sqlBuilder.toString());
        EntityCollection entityCollection = new EntityCollection();
        for (Map<String, Object> row : result) {
            Entity resEntity = getEntity(edmEntitySet, row, expandOption, odata, serviceMetadata);
            entityCollection.getEntities().add(resEntity);
        }

        return entityCollection;
    }

    @Override
    public int countEntityCollection(UriInfo uriInfo) throws ODataApplicationException {
        return 0;
    }

    @Override
    public Entity readEntity(UriInfo uriInfo, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
        List<UriResource> uriResources = uriInfoResource.getUriResourceParts();

        UriInfoContext uriInfoContext = getUriInfoContext(odata, serviceMetadata, uriResources);
        return uriInfoContext.refEntity;
    }

    private Entity getEntity(EdmEntitySet edmEntitySet, List<UriParameter> keyParameters, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        Map row = getEntityRow(edmEntitySet, where(edmEntitySet, keyParameters));
        return getEntity(edmEntitySet, row, new ExpandOptionImpl(), odata, serviceMetadata);
    }

    private Entity getEntity(EdmEntitySet edmEntitySet, Map entityRow, ExpandOption expandOption, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {

        DeserializerResult deserializerResult;
        try {
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            PGobject jsonObject = (PGobject) entityRow.get("data");
            deserializerResult = deserializer.entity(IOUtils.toInputStream(jsonObject.getValue()), edmEntitySet.getEntityType());
        } catch (DeserializerException e) {
            throw new ODataApplicationException("DeserializerException:" + e.getMessage(), HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.getDefault());
        }
        Map dataRow = getData(entityRow);
        Entity entity = deserializerResult.getEntity();

        for (ExpandItem expandItem : expandOption.getExpandItems()) {
            UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) expandItem.getResourcePath().getUriResourceParts().get(0);
            EdmNavigationProperty navigationProperty = uriResourceNavigation.getProperty();
            String refName = navigationProperty.getName();
            if (!dataRow.containsKey(refName)) continue;

            Link link = entity.getNavigationLink(refName);
            link.setRel(refName);
            link.setTitle(refName);
            if (navigationProperty.isCollection()) {
                EdmEntitySet refEdmEntitySet = getNavigationPropertyBindingSet(refName, edmEntitySet, serviceMetadata);
                EntityCollection refEntityCollection = readEntitySetData(refEdmEntitySet, -1, -1, whereIds((List<Map>) dataRow.get(refName)), "", new ExpandOptionImpl(), odata, serviceMetadata);
                link.setInlineEntitySet(refEntityCollection);
            } else {
                EdmEntitySet refEdmEntitySet = getNavigationPropertyBindingSet(refName, edmEntitySet, serviceMetadata);
                Map row = getEntityRow(refEdmEntitySet, where(refEdmEntitySet, (Map) dataRow.get(refName)));
                link.setInlineEntity(getEntity(refEdmEntitySet, row, new ExpandOptionImpl(), odata, serviceMetadata));
            }

        }
        return entity;
    }

    private Map getEntityRow(EdmEntitySet edmEntitySet, String where) throws ODataApplicationException {
        StringBuffer sqlBuilder = new StringBuffer("select * from entity ");
        sqlBuilder.append(where);
        List<Map<String, Object>> result = jdbcTemplate.queryForList(sqlBuilder.toString());
        if (result.size() == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ENGLISH);
        return result.get(0);
    }

    private Map getData(Map entityRow) throws ODataApplicationException {
        ObjectMapper mapper = new ObjectMapper();
        PGobject jsonObject = (PGobject) entityRow.get("data");
        try {
            return mapper.readValue(jsonObject.getValue(), Map.class);
        } catch (IOException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.EXPECTATION_FAILED.getStatusCode(), Locale.ENGLISH);
        }
    }

    @Override
    @Transactional
    public Entity createEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        InputStream jsonStream = null;
        try {
            UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
            List<UriResource> uriResources = uriInfoResource.getUriResourceParts();

            UriInfoContext uriInfoContext = getUriInfoContext(odata, serviceMetadata, uriResources);

            Entity refEntity = uriInfoContext.getRefEntity();
            EdmEntitySet edmEntitySet = uriInfoContext.getEdmEntitySet();

            InputStream requestInputStream = request.getBody();
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            DeserializerResult result = deserializer.entity(requestInputStream, edmEntitySet.getEntityType());
            Entity requestEntity = result.getEntity();
            jsonStream = serializer.entity(serviceMetadata, edmEntitySet.getEntityType(), requestEntity, EntitySerializerOptions.with().build());

            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(IOUtils.toString(jsonStream));
            jdbcTemplate.update("INSERT INTO entity(\"repositoryId\", \"name\", \"data\" ) VALUES (?, ?, ?)", servlet.getRepositoryId(), edmEntitySet.getName(), jsonObject);


            if (refEntity != null) {//添加引用关系
                Map refKeyPredicates = serializer.getKeyPredicates(edmEntitySet.getEntityType(), requestEntity);
                Map navigationMap = new HashMap<>();
                navigationMap.put(uriInfoContext.getEdmNavigationProperty().getName(), new ArrayList() {{
                    add(refKeyPredicates);
                }});
                updateNavigationProperty(uriInfoContext.getRefEdmEntitySet(), uriInfoContext.getKeyParams(), navigationMap);
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

    private UriInfoContext getUriInfoContext(OData odata, ServiceMetadata serviceMetadata, List<UriResource> uriResources) throws ODataApplicationException {
        UriInfoContext context = new UriInfoContext();
        for (int i = 0; i < uriResources.size(); i++) {
            UriResource uriResource = uriResources.get(i);
            if (uriResource.getKind() == UriResourceKind.entitySet) {
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
                context.setRefEdmEntitySet(context.getEdmEntitySet());
                context.setEdmEntitySet(uriResourceEntitySet.getEntitySet());
                if (uriResourceEntitySet.getKeyPredicates() == null || uriResourceEntitySet.getKeyPredicates().size() == 0) {
                    break;
                }
                context.setKeyParams(uriResourceEntitySet.getKeyPredicates());
                context.setRefEntity(getEntity(uriResourceEntitySet.getEntitySet(), uriResourceEntitySet.getKeyPredicates(), odata, serviceMetadata));
            } else if (uriResource.getKind() == UriResourceKind.navigationProperty) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) uriResource;
                context.setEdmNavigationProperty(uriResourceNavigation.getProperty());
                EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
                for (EdmNavigationPropertyBinding edmNavigationPropertyBinding : context.getEdmEntitySet().getNavigationPropertyBindings()) {
                    if (edmNavigationPropertyBinding.getPath().equals(edmNavigationProperty.getName())) {
                        context.setRefEdmEntitySet(context.getEdmEntitySet());
                        context.setEdmEntitySet(serviceMetadata.getEdm().getEntityContainer().getEntitySet(edmNavigationPropertyBinding.getTarget()));
                    }
                }
                if (uriResourceNavigation.getKeyPredicates() == null || uriResourceNavigation.getKeyPredicates().size() == 0)
                    break;
                context.setKeyParams(uriResourceNavigation.getKeyPredicates());
                context.setRefEntity(getEntity(context.getEdmEntitySet(), uriResourceNavigation.getKeyPredicates(), odata, serviceMetadata));

            }

        }
        return context;
    }


    @Override
    @Transactional
    public void updateEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        try {
            UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
            List<UriResource> uriResources = uriInfoResource.getUriResourceParts();
            UriInfoContext uriInfoContext = getUriInfoContext(odata, serviceMetadata, uriResources);

            Entity entity = uriInfoContext.getRefEntity();
            EdmEntitySet edmEntitySet = uriInfoContext.getEdmEntitySet();

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
            updateEntity(edmEntitySet, uriInfoContext.getKeyParams(), entity, serviceMetadata);

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
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.getDefault());
    }

    private void updateEntityRow(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, Map entityRow) throws ODataApplicationException {
        ObjectMapper mapper = new ObjectMapper();

        StringBuffer sqlBuilder = new StringBuffer("update entity set data = ? ");
        where(sqlBuilder, edmEntitySet, keyParams);
        PGobject jsonObject = new PGobject();
        jsonObject.setType("jsonb");
        try {
            jsonObject.setValue(mapper.writeValueAsString(entityRow));
        } catch (SQLException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } catch (JsonProcessingException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        }

        int result = jdbcTemplate.update(sqlBuilder.toString(), jsonObject);
        if (result == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.getDefault());
    }


    private void updateNavigationProperty(EdmEntitySet edmEntitySet, List<UriParameter> keyParams, Map propertyJsonMap) throws ODataApplicationException, SerializerException, IOException, SQLException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map row = getEntityRow(edmEntitySet, where(edmEntitySet, keyParams));
        PGobject jsonObject = (PGobject) row.get("data");
        Map entityMap = objectMapper.readValue(jsonObject.getValue(), Map.class);
        EdmEntityType edmEntityType = edmEntitySet.getEntityType();
        for (Object proeprtyKey : propertyJsonMap.keySet()) {
            EdmNavigationProperty edmNavigationProperty = edmEntityType.getNavigationProperty(proeprtyKey.toString());
            if (edmNavigationProperty == null || !propertyJsonMap.containsKey(proeprtyKey)) continue;
            if (edmNavigationProperty.isCollection()) {
                List valueList = entityMap.containsKey(proeprtyKey) ? (List) entityMap.get(proeprtyKey) : new ArrayList<>();
                valueList.addAll((Collection) propertyJsonMap.get(proeprtyKey));
                entityMap.put(proeprtyKey, valueList);
            } else {
                List list = (List) propertyJsonMap.get(proeprtyKey);
                entityMap.put(proeprtyKey, list.get(0));
            }
        }
        updateEntityRow(edmEntitySet, keyParams, entityMap);
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
    @Transactional
    public void deleteEntity(UriInfo uriInfo) throws ODataApplicationException {

    }


    private EdmEntitySet getNavigationPropertyBindingSet(String refName, EdmEntitySet edmEntitySet, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        for (EdmNavigationPropertyBinding edmNavigationPropertyBinding : edmEntitySet.getNavigationPropertyBindings()) {
            if (edmNavigationPropertyBinding.getPath().equals(refName)) {
                return serviceMetadata.getEdm().getEntityContainer().getEntitySet(edmNavigationPropertyBinding.getTarget());
            }
        }
        throw new ODataApplicationException("EntitySet not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.CHINESE);
    }


    private String where(EdmEntitySet edmEntitySet, Map keyParams) {
        StringBuffer sqlBuilder = new StringBuffer();
        sqlBuilder.append(" where \"repositoryId\" = '").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        for (Object key : keyParams.keySet()) {
            String value = keyParams.get(key).toString().replaceAll("'", "");
            sqlBuilder.append(" and data->>'").append(key).append("' =  '").append(value + "'");
        }
        return sqlBuilder.toString();
    }

    private String where(EdmEntitySet edmEntitySet, List<UriParameter> keyParams) {
        StringBuffer sqlBuilder = new StringBuffer();
        where(sqlBuilder, edmEntitySet, keyParams);
        return sqlBuilder.toString();
    }

    private void where(StringBuffer sqlBuilder, EdmEntitySet edmEntitySet, List<UriParameter> keyParams) {
        sqlBuilder.append(" where \"repositoryId\" = '").append(servlet.getRepositoryId()).append("' and name='").append(edmEntitySet.getName()).append("'");
        for (UriParameter keyParam : keyParams) {
            String value = keyParam.getText().replaceAll("'", "");
            sqlBuilder.append(" and data->>'").append(keyParam.getName()).append("' =  '").append(value).append("'");
        }
    }

    private String whereIds(EntityCollection entityCollection) {
        List<String> entityConditions = new ArrayList<String>();
        for (Entity entity : entityCollection.getEntities()) {
            List<String> keyConditions = new ArrayList<String>();
            for (Property property : entity.getProperties()) {
                keyConditions.add("data->>'" + property.getName() + "' =  '" + property.getValue() + "'");
            }
            entityConditions.add(" ( " + String.join(" and ", keyConditions) + " )");

        }

        return String.join(" or ", entityConditions);

    }

    private String whereIds(List<Map> keyMapList) {
        List<String> entityConditions = new ArrayList<String>();
        for (Map keyMap : keyMapList) {
            List<String> keyConditions = new ArrayList<String>();
            for (Object key : keyMap.keySet()) {
                keyConditions.add("data->>'" + key + "' =  '" + keyMap.get(key) + "'");
            }
            entityConditions.add(" ( " + String.join(" and ", keyConditions) + " )");

        }

        return String.join(" or ", entityConditions);

    }

    class UriInfoContext {
        Entity refEntity = null;
        EdmEntitySet refEdmEntitySet = null;
        EdmEntitySet edmEntitySet = null;
        List<UriParameter> keyParams;
        EdmNavigationProperty edmNavigationProperty;

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

        public EdmEntitySet getRefEdmEntitySet() {
            return refEdmEntitySet;
        }

        public void setRefEdmEntitySet(EdmEntitySet refEdmEntitySet) {
            this.refEdmEntitySet = refEdmEntitySet;
        }

        public EdmNavigationProperty getEdmNavigationProperty() {
            return edmNavigationProperty;
        }

        public void setEdmNavigationProperty(EdmNavigationProperty edmNavigationProperty) {
            this.edmNavigationProperty = edmNavigationProperty;
        }
    }

}
