package com.idea.ohmydata;


import com.idea.ohmydata.persisitence.visitor.FilterVisitor;
import org.apache.commons.lang3.StringUtils;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.*;
import org.apache.olingo.server.api.uri.queryoption.*;
import org.apache.olingo.server.core.uri.queryoption.ExpandItemImpl;
import org.apache.olingo.server.core.uri.queryoption.ExpandOptionImpl;
import org.apache.olingo.server.core.uri.queryoption.SelectItemImpl;
import org.apache.olingo.server.core.uri.queryoption.SelectOptionImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class UriInfoUtils {


    public static EdmEntitySet getEdmEntitySet(UriInfoResource uriInfo) throws ODataApplicationException {

        List<UriResource> resourcePaths = uriInfo.getUriResourceParts();
        if (!(resourcePaths.get(0) instanceof UriResourceEntitySet)) {
            throw new ODataApplicationException("Invalid resource type for first segment.", HttpStatusCode.NOT_IMPLEMENTED
                    .getStatusCode(), Locale.ENGLISH);
        }

        UriResourceEntitySet uriResource = (UriResourceEntitySet) resourcePaths.get(0);
        return uriResource.getEntitySet();
    }

    public static SelectOption getSelect(UriInfo uriInfo) {

        SelectOption selectOption = null;
        Collection<SystemQueryOption> systemQueryOptions = uriInfo.getSystemQueryOptions();
        for (SystemQueryOption systemQueryOption : systemQueryOptions) {
            if (systemQueryOption.getKind().equals(SystemQueryOptionKind.SELECT)) {
                selectOption = (SelectOption) systemQueryOption;
            }
        }

        if (selectOption == null) {
            selectOption = new SelectOptionImpl();
            ((SelectOptionImpl) selectOption).setSelectItems(new ArrayList<SelectItemImpl>() {{
                add(new SelectItemImpl().setStar(true));
            }});
        }

        return selectOption;
    }

    public static ExpandOption getExpand(UriInfo uriInfo) throws ODataApplicationException {
        ExpandOptionImpl responseExpandOption = new ExpandOptionImpl();
        Collection<SystemQueryOption> systemQueryOptions = uriInfo.getSystemQueryOptions();
        for (SystemQueryOption systemQueryOption : systemQueryOptions) {
            if (systemQueryOption.getKind().equals(SystemQueryOptionKind.EXPAND)) {

                ExpandOption expandOption = (ExpandOption) systemQueryOption;
                for (ExpandItem expandItem : expandOption.getExpandItems()) {
                    ExpandItemImpl responseExpandItem = new ExpandItemImpl();
                    responseExpandItem.setResourcePath(expandItem.getResourcePath());
                    responseExpandOption.addExpandItem(responseExpandItem);
                }
            }
        }
        return responseExpandOption;
    }

    public static String getOrderBy(UriInfo uriInfo) throws ODataApplicationException {
        String orderBy = StringUtils.EMPTY;
        Collection<SystemQueryOption> systemQueryOptions = uriInfo.getSystemQueryOptions();
        for (SystemQueryOption systemQueryOption : systemQueryOptions) {
            if (systemQueryOption.getKind().equals(SystemQueryOptionKind.ORDERBY)) {

                OrderByOption orderByOption = (OrderByOption) systemQueryOption;
                FilterVisitor filterVisitor = new FilterVisitor();
                for (OrderByItem orderByItem : orderByOption.getOrders()) {
                    orderBy += filterVisitor.visit(orderByItem.getExpression()).toString();
                    if (orderByItem.isDescending()) orderBy += " desc,";
                    else orderBy += " asc,";
                }
                if (orderBy != null)
                    orderBy = "order by" + orderBy.substring(0, orderBy.length() - 1);
            }
        }
        return orderBy;
    }

    public static String getFilter(UriInfo uriInfo) throws ODataApplicationException {
        Collection<SystemQueryOption> systemQueryOptions = uriInfo.getSystemQueryOptions();
        for (SystemQueryOption systemQueryOption : systemQueryOptions) {
            if (systemQueryOption.getKind().equals(SystemQueryOptionKind.FILTER)) {
                FilterOption filterOption = (FilterOption) systemQueryOption;
                FilterVisitor filterVisitor = new FilterVisitor();
                Object res = filterVisitor.visit(filterOption.getExpression());
                return res.toString();
            }
        }
        return StringUtils.EMPTY;
    }

    public static int getSkip(UriInfo uriInfo) {
        int skip = -1;
        Collection<SystemQueryOption> systemQueryOptions = uriInfo.getSystemQueryOptions();
        for (SystemQueryOption systemQueryOption : systemQueryOptions) {
            if (systemQueryOption.getKind().equals(SystemQueryOptionKind.SKIP)) {
                SkipOption skipOption = (SkipOption) systemQueryOption;
                skip = skipOption.getValue();
            }
        }
        return skip;
    }


    public static int getTop(UriInfo uriInfo) {
        int top = -1;
        Collection<SystemQueryOption> systemQueryOptions = uriInfo.getSystemQueryOptions();
        for (SystemQueryOption systemQueryOption : systemQueryOptions) {
            if (systemQueryOption.getKind().equals(SystemQueryOptionKind.TOP)) {
                TopOption topOption = (TopOption) systemQueryOption;
                top = topOption.getValue();
            }
        }
        return top;
    }

    public static List<UriParameter> getKeyPredicates(UriInfo uriInfo) {
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        UriResourceEntitySet uriEntityset = (UriResourceEntitySet) resourceParts.get(0);
        return uriEntityset.getKeyPredicates();
    }

    public static EdmProperty EdmProperty(UriInfo uriInfo) {
        List<UriResource> resourceParts = uriInfo.getUriResourceParts();
        UriResourceProperty uriProperty = (UriResourceProperty) resourceParts.get(resourceParts.size() - 1);
        return uriProperty.getProperty();
    }
}
