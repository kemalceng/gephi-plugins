/*
Copyright 2008-2011 Gephi
Authors : Luiz Ribeiro <luizribeiro@gmail.com>
Website : http://www.gephi.org

This file is part of Gephi.

Gephi is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

Gephi is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Gephi.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gephi.scripting.wrappers;

import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeType;
import org.gephi.data.attributes.api.AttributeUtils;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.api.Range;
import org.gephi.filters.plugin.attribute.AttributeEqualBuilder;
import org.gephi.filters.plugin.attribute.AttributeRangeBuilder;
import org.gephi.filters.plugin.attribute.AttributeRangeBuilder.AttributeRangeFilter;
import org.gephi.filters.spi.Filter;
import org.gephi.scripting.util.GyNamespace;
import org.openide.util.Lookup;
import org.python.core.Py;
import org.python.core.PyObject;

/**
 *
 * @author Luiz Ribeiro
 */
public class GyAttributeColumn extends GyAttribute {

    private AttributeColumn underlyingAttributeColumn;

    public GyAttributeColumn(GyNamespace namespace, AttributeColumn underlyingAttributeColumn) {
        super(namespace);
        this.underlyingAttributeColumn = underlyingAttributeColumn;
    }

    @Override
    public String toString() {
        if (AttributeUtils.getDefault().isNodeColumn(underlyingAttributeColumn)) {
            return "Node Attribute '" + underlyingAttributeColumn.getId() + "' (" + underlyingAttributeColumn.getType() + ")";
        } else {
            return "Edge Attribute '" + underlyingAttributeColumn.getId() + "' (" + underlyingAttributeColumn.getType() + ")";
        }
    }

    @Override
    public Class getAttributeType() {
        return underlyingAttributeColumn.getType().getType();
    }

    @Override
    public boolean isNodeAttribute() {
        return AttributeUtils.getDefault().isNodeColumn(underlyingAttributeColumn);
    }

    @Override
    protected Query buildRangeQuery(Range range) {
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        AttributeRangeFilter attributeRangeFilter;
        Query query;

        if (underlyingAttributeColumn.getType() == AttributeType.BOOLEAN) {
            throw Py.TypeError("unsupported operator for attribute type '" + underlyingAttributeColumn.getType() + "'");
        }

        if (AttributeUtils.getDefault().isNodeColumn(underlyingAttributeColumn)) {
            attributeRangeFilter = new AttributeRangeBuilder.NodeAttributeRangeFilter(underlyingAttributeColumn);
        } else {
            attributeRangeFilter = new AttributeRangeBuilder.EdgeAttributeRangeFilter(underlyingAttributeColumn);
        }

        query = filterController.createQuery(attributeRangeFilter);
        attributeRangeFilter.setRange(range);

        return query;
    }

    @Override
    protected Query buildEqualsQuery(PyObject match) {
        FilterController filterController = Lookup.getDefault().lookup(FilterController.class);
        Filter attributeEqualFilter;
        Query query;

        if (AttributeUtils.getDefault().isNumberColumn(underlyingAttributeColumn)) {
            AttributeEqualBuilder.EqualNumberFilter filter;

            // FIXME: this is not working correctly yet

            if (AttributeUtils.getDefault().isNodeColumn(underlyingAttributeColumn)) {
                filter = new AttributeEqualBuilder.NodeEqualNumberFilter(underlyingAttributeColumn);
            } else {
                filter = new AttributeEqualBuilder.EdgeEqualNumberFilter(underlyingAttributeColumn);
            }

            attributeEqualFilter = filter;
            query = filterController.createQuery(attributeEqualFilter);

            filter.setMatch((Number) match.__tojava__(underlyingAttributeColumn.getType().getType()));
        } else if (AttributeUtils.getDefault().isStringColumn(underlyingAttributeColumn)) {
            AttributeEqualBuilder.EqualStringFilter filter;

            if (AttributeUtils.getDefault().isNodeColumn(underlyingAttributeColumn)) {
                filter = new AttributeEqualBuilder.NodeEqualStringFilter();
            } else {
                filter = new AttributeEqualBuilder.EdgeEqualStringFilter();
            }

            attributeEqualFilter = filter;
            query = filterController.createQuery(attributeEqualFilter);

            filter.setColumn(underlyingAttributeColumn);
            filter.setPattern(match.toString());
        } else if (underlyingAttributeColumn.getType() == AttributeType.BOOLEAN) {
            AttributeEqualBuilder.EqualBooleanFilter filter;

            if (AttributeUtils.getDefault().isNodeColumn(underlyingAttributeColumn)) {
                filter = new AttributeEqualBuilder.NodeEqualBooleanFilter();
            } else {
                filter = new AttributeEqualBuilder.EdgeEqualBooleanFilter();
            }

            query = filterController.createQuery(filter);

            filter.setColumn(underlyingAttributeColumn);
            filter.setMatch((Boolean) match.__tojava__(Boolean.class));
        } else {
            throw Py.TypeError("unsupported operator for attribute type '" + underlyingAttributeColumn.getType() + "'");
        }

        return query;
    }
}