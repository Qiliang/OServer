package com.idea.ohmydata.persistence.postgres;

import com.idea.ohmydata.ODataServlet;
import com.idea.ohmydata.UriInfoUtils;
import com.idea.ohmydata.persistence.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.olingo.commons.api.data.*;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ODataFormat;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.commons.core.edm.AbstractEdmAnnotatable;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.core.uri.parser.Parser;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.*;

@Service
class PostgresDataService implements PersistenceDataService {

    private static Field edmField;

    static {
        edmField = FieldUtils.getField(AbstractEdmAnnotatable.class, "edm", true);
    }

    @Autowired
    private ODataServlet servlet;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void createReference(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        try {
            UriInfoContext ctx = getUriInfoContext(uriInfo);
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            DeserializerResult result = deserializer.entityReferences(request.getBody());

            int rawBaseUriIndex = request.getRawBaseUri().length();
            for (URI uri : result.getEntityReferences()) {
                UriInfo refUriInfo = new Parser().parseUri(uri.toString().substring(rawBaseUriIndex), null, null, serviceMetadata.getEdm());
                UriInfoContext refContext = getUriInfoContext(refUriInfo);
                JsonObj keyParams = JsonObj.parse(refContext.keyParams, ctx.edmNavigationProperty.getType());
                createRef(ctx.edmNavigationProperty.getName(), ctx.entity.getKeyObj(), keyParams);
            }

        } catch (DeserializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
        } catch (Exception e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        } finally {

        }
    }

    @Override
    public DbEntityCollection readEntityCollection(UriInfo uriInfo, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        DbEntityCollection dbEntityCollection = new DbEntityCollection();
        UriInfoContext ctx = getUriInfoContext(uriInfo);

        if (ctx.edmNavigationProperty != null) {
            JsonCollection jsonObjs = retrieveRefEntityCollection(ctx, uriInfo.asUriInfoResource(), new JsonObj());
            dbEntityCollection.setEntityType(ctx.getType());
            dbEntityCollection.setEntityCollection(jsonObjs.toEntityCollection(odata));
        } else {
            JsonCollection jsonObjs = retrieveEntityCollection(ctx.getType(), uriInfo.asUriInfoResource(), new JsonObj());
            dbEntityCollection.setEntityType(ctx.getType());
            dbEntityCollection.setEntityCollection(jsonObjs.toEntityCollection(odata));
        }
        return dbEntityCollection;
    }

    private JsonCollection retrieveEntityCollection(EdmEntityType edmEntityType, UriInfoResource uriInfoResource, JsonObj condition) throws ODataApplicationException {
        StringBuilder sql = new StringBuilder(255);
        sql.append("select data from entity where \"repositoryId\" = ").append("'").append(servlet.getRepositoryId()).append("' and type='")
                .append(edmEntityType.getFullQualifiedName().getFullQualifiedNameAsString()).append("'");
        return retrieveEntityCollection(edmEntityType, uriInfoResource, sql.toString(), condition);
    }

    private JsonCollection retrieveRefEntityCollection(UriInfoContext ctx, UriInfoResource uriInfoResource, JsonObj condition) throws ODataApplicationException {
        StringBuilder sql = new StringBuilder(255);
        sql.append("select t.type, t.data from ");
        String type = String.format("%s/%s", ctx.entity.getEdmEntityType().getFullQualifiedName().getFullQualifiedNameAsString(), ctx.edmNavigationProperty.getName());
        sql.append("(select data from entity where type='").append(type).append("' and ").append("\"repositoryId\" ='").append(servlet.getRepositoryId()).append("' and ")
                .append(ctx.buildRefTableKeys()).append(") ref");
        sql.append(" inner join ");
        sql.append("(select type, data from entity where ").append(ctx.buildDerivedTypes())
                .append(" and ").append("\"repositoryId\" ='").append(servlet.getRepositoryId()).append("') t");
        sql.append(" on ");
        sql.append(ctx.buildInnerJoinKeys("ref"));
        return retrieveEntityCollection(ctx.getType(), uriInfoResource, sql.toString(), condition);
    }

    private JsonCollection retrieveEntityCollection(EdmEntityType edmEntityType, UriInfoResource uriInfoResource, String baseSQL, JsonObj condition) throws ODataApplicationException {
        int top = uriInfoResource.getTopOption() == null ? 0 : uriInfoResource.getTopOption().getValue();
        int skip = uriInfoResource.getSkipOption() == null ? 0 : uriInfoResource.getSkipOption().getValue();
        String orderBy = UriInfoUtils.getOrderBy(uriInfoResource.getOrderByOption());
        ExpandOption expandOption = uriInfoResource.getExpandOption() == null ? new ExpandOptionImpl() : uriInfoResource.getExpandOption();
        StringBuilder sql = new StringBuilder(baseSQL);
        sql.append(" and ( ").append(condition.toSQL()).append(" )");
        sql.append(orderBy);
        if (top > 0) sql.append(" limit ").append(top);
        if (skip > 0) sql.append(" offset ").append(skip);

        List<Map<String, Object>> result = jdbcTemplate.queryForList(sql.toString());
        JsonCollection entityCollection = new JsonCollection();
        for (Map<String, Object> row : result) {
            EdmEntityType entityType = seekType(row.get("type").toString(), edmEntityType);
            entityCollection.add(retrieveEntity(entityType, expandOption, (PGobject) row.get("data")));
        }
        return entityCollection;

    }


    @Override
    public int countEntityCollection(UriInfo uriInfo) throws ODataApplicationException {
        return 0;
    }

    @Override
    public Entity readEntity(UriInfo uriInfo, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        UriInfoContext ctx = getUriInfoContext(uriInfo);
        return ctx.entity.toEntity(odata);
    }

    @Override
    @Transactional
    public Entity createEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {

        try {
            UriInfoContext ctx = getUriInfoContext(uriInfo);
            EdmNavigationProperty edmNavigationProperty = ctx.edmNavigationProperty;
            byte[] content = IOUtils.toByteArray(new AutoCloseInputStream(request.getBody()));
            JsonObj entity = JsonObj.parse(content, ctx.getType());
            jdbcTemplate.update("INSERT INTO entity(\"repositoryId\", \"type\", \"data\" ) VALUES (?, ?, ?)", servlet.getRepositoryId(), entity.getEdmEntityType().getFullQualifiedName().getFullQualifiedNameAsString(), entity.toPgObject());
            if (ctx.entity != null) {
                createRef(edmNavigationProperty.getName(), ctx.entity.getKeyObj(), entity.getKeyObj());
            }
            ODataDeserializer deserializer = odata.createDeserializer(ODataFormat.JSON);
            return deserializer.entity(toInputStream(content), ctx.getType()).getEntity();

        } catch (DeserializerException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
        } catch (Exception e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        }
    }

    private InputStream toInputStream(byte[] content) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
        return new AutoCloseInputStream(inputStream);
    }

    private void createRef(String navigationName, JsonObj source, JsonObj target) throws ODataApplicationException {
        JsonObj data = new JsonObj();
        data.put("source", source);
        data.put("sourceType", source.getEdmEntityType().getFullQualifiedName().getFullQualifiedNameAsString());
        data.put("target", target);
        data.put("targetType", target.getEdmEntityType().getFullQualifiedName().getFullQualifiedNameAsString());
        JsonObj ref = new JsonObj();
        ref.put(navigationName, data);
        String type = String.format("%s/%s", source.getEdmEntityType().getFullQualifiedName().getFullQualifiedNameAsString(), navigationName);
        jdbcTemplate.update("INSERT INTO entity(\"repositoryId\", \"type\", \"data\" ) VALUES (?, ?, ?)", servlet.getRepositoryId(), type, ref.toPgObject());
    }

    private JsonObj retrieveEntity(JsonObj condition, ExpandOption expandOption) throws ODataApplicationException {

        StringBuffer sql = new StringBuffer("select * from entity ");
        sql.append("where \"repositoryId\"='").append(servlet.getRepositoryId()).append("' ");
        sql.append(" and (").append(condition.getKeyObj().toSQL()).append(")");
        List<Map<String, Object>> resultSet = jdbcTemplate.queryForList(sql.toString());
        if (resultSet.size() == 0)
            throw new ODataApplicationException("Entity Not Found", HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());

        EdmEntityType entityType = seekType(resultSet.get(0).get("type").toString(), condition.getEdmEntityType());
        return retrieveEntity(entityType, expandOption, (PGobject) resultSet.get(0).get("data"));
    }

    private JsonObj retrieveEntity(EdmEntityType edmEntityType, ExpandOption expandOption, PGobject pGobject) throws ODataApplicationException {
        JsonObj entityObj = JsonObj.parse(pGobject.getValue(), edmEntityType);
        for (ExpandItem expandItem : expandOption.getExpandItems()) {
            UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) expandItem.getResourcePath().getUriResourceParts().get(0);
            EdmNavigationProperty edmNavigationProperty = uriResourceNavigation.getProperty();
            JsonObj expandCondition = new JsonObj();
            UriInfoContext ctx = new UriInfoContext();
            ctx.entity = entityObj;
            ctx.edmNavigationProperty = edmNavigationProperty;
            List<JsonObj> jsonObjList = retrieveRefEntityCollection(ctx, expandItem.getResourcePath(), expandCondition);
            if (edmNavigationProperty.isCollection()) {
                entityObj.put(edmNavigationProperty.getName(), jsonObjList);
            } else {
                entityObj.put(edmNavigationProperty.getName(), jsonObjList.get(0));
            }
        }

        return entityObj;
    }


    private EdmEntityType seekType(String namespaceAndName, EdmType seed) throws ODataApplicationException {
        try {
            Edm edm = (Edm) edmField.get(seed);
            return edm.getEntityType(new FullQualifiedName(namespaceAndName));
        } catch (IllegalAccessException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault(), e);
        }
    }

    private UriInfoContext getUriInfoContext(UriInfo uriInfo) throws ODataApplicationException {
        UriInfoResource uriInfoResource = uriInfo.asUriInfoResource();
        List<UriResource> uriResources = uriInfoResource.getUriResourceParts();
        UriInfoContext context = new UriInfoContext();
        for (int i = 0; i < uriResources.size(); i++) {
            UriResource uriResource = uriResources.get(i);
            ExpandOption expandOption = uriInfo.getExpandOption() != null && i == uriResources.size() - 1 ? uriInfo.getExpandOption() : new ExpandOptionImpl();
            if (uriResource.getKind() == UriResourceKind.entitySet) {
                UriResourceEntitySet uriResourceEntitySet = (UriResourceEntitySet) uriResource;
                context.edmEntityType = (EdmEntityType) uriResourceEntitySet.getTypeFilterOnCollection();
                context.entitySet = uriResourceEntitySet.getEntitySet();
                if (uriResourceEntitySet.getKeyPredicates() == null || uriResourceEntitySet.getKeyPredicates().size() == 0)
                    break;
                context.edmEntityType = (EdmEntityType) uriResourceEntitySet.getTypeFilterOnEntry();
                context.keyParams = uriResourceEntitySet.getKeyPredicates();
                context.entity = retrieveEntity(JsonObj.parse(context.keyParams, context.getType()), expandOption);
            } else if (uriResource.getKind() == UriResourceKind.navigationProperty) {
                UriResourceNavigation uriResourceNavigation = (UriResourceNavigation) uriResource;
                context.edmNavigationProperty = uriResourceNavigation.getProperty();
                context.edmEntityType = (EdmEntityType) uriResourceNavigation.getTypeFilterOnCollection();
                if (uriResourceNavigation.getKeyPredicates() == null || uriResourceNavigation.getKeyPredicates().size() == 0)
                    break;
                context.edmEntityType = (EdmEntityType) uriResourceNavigation.getTypeFilterOnEntry();
                context.keyParams = uriResourceNavigation.getKeyPredicates();
                JsonObj condition = JsonObj.parse(context.keyParams, context.getType());

                context.entity = retrieveEntity(condition, expandOption);
            }

        }
        return context;
    }


    @Override
    @Transactional
    public void updateEntity(UriInfo uriInfo, ODataRequest request, OData odata, ServiceMetadata serviceMetadata) throws ODataApplicationException {
        try {
            UriInfoContext ctx = getUriInfoContext(uriInfo);
            byte[] content = IOUtils.toByteArray(new AutoCloseInputStream(request.getBody()));
            JsonObj updateEntity = JsonObj.parse(content, ctx.getType());
            for (String propName : ctx.entity.getEdmEntityType().getPropertyNames()) {
                if (ctx.entity.isKey(propName)) continue;
                if (request.getMethod().equals(HttpMethod.PUT)) ctx.entity.remove(propName);
                if (updateEntity.containsKey(propName)) {
                    ctx.entity.put(propName, updateEntity.get(propName));
                }
            }


            updateEntity(ctx.entity);
        } catch (IOException e) {
            throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault());
        }
    }

    private void updateEntity(JsonObj entity) throws ODataApplicationException {
        StringBuilder sql = new StringBuilder("update entity set data = ? ");
        sql.append("where ").append(entity.getKeyObj().toSQL());

        int result = jdbcTemplate.update(sql.toString(), entity.toPgObject());
        if (result == 0)
            throw new ODataApplicationException("Entity not found", HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.getDefault());
    }

    @Override
    @Transactional
    public void deleteEntity(UriInfo uriInfo) throws ODataApplicationException {

    }


    class UriInfoContext {
        JsonObj entity;
        EdmEntityType edmEntityType;
        EdmEntitySet entitySet;
        List<UriParameter> keyParams;
        EdmNavigationProperty edmNavigationProperty;


        public EdmEntityType getType() {
            if (edmEntityType != null) return edmEntityType;
            return edmNavigationProperty == null ? entitySet.getEntityType() : edmNavigationProperty.getType();
        }

        public String buildRefTableKeys() {
            List<String> joinCondition = new ArrayList<>();
            JsonObj keyValues = this.entity.getKeyObj();
            for (String key : keyValues.keySet()) {
                joinCondition.add(String.format("(data->'%s'->'source'->>'%s'='%s' )", this.edmNavigationProperty.getName(), key, keyValues.get(key)));
            }
            return String.join(" and ", joinCondition);
        }

        public String buildInnerJoinKeys(String alias) {
            List<String> joinCondition = new ArrayList<>();
            for (String key : this.getType().getKeyPredicateNames()) {
                joinCondition.add(String.format("(%s.data->'%s'->'target'->'%s'=t.data->'%s' )", alias, this.edmNavigationProperty.getName(), key, key));
            }
            return String.join(" and ", joinCondition);
        }


        public String buildDerivedTypes() throws ODataApplicationException {
            List<String> joinCondition = new ArrayList<>();
            for (EdmEntityType type : this.getDerivedTypes()) {
                joinCondition.add(String.format("( type='%s' )", type.getFullQualifiedName().getFullQualifiedNameAsString()));
            }
            return "(" + String.join(" or ", joinCondition) + ")";
        }

        public List<EdmEntityType> getDerivedTypes() throws ODataApplicationException {
            EdmEntityType sourceType = getType();
            List<EdmEntityType> derivedTypes = new ArrayList<>();
            try {
                Edm edm = (Edm) edmField.get(sourceType);
                for (EdmEntityType entityType : edm.getSchema(sourceType.getNamespace()).getEntityTypes()) {
                    if (isDerived(sourceType, entityType)) derivedTypes.add(entityType);
                }
                return derivedTypes;

            } catch (IllegalAccessException e) {
                throw new ODataApplicationException(e.getMessage(), HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.getDefault(), e);
            }
        }

        private boolean isDerived(EdmEntityType source, EdmEntityType target) throws ODataApplicationException {
            EdmEntityType type = target;
            while (type != null) {
                if (type.getFullQualifiedName().equals(source.getFullQualifiedName()))
                    return true;
                type = type.getBaseType();
            }

            return false;
        }
    }

}
