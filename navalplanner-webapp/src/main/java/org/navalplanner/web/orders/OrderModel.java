/*
 * This file is part of ###PROJECT_NAME###
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 * Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.web.orders;

import static org.navalplanner.web.I18nHelper._;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.navalplanner.business.advance.entities.AdvanceMeasurement;
import org.navalplanner.business.advance.entities.DirectAdvanceAssignment;
import org.navalplanner.business.advance.entities.IndirectAdvanceAssignment;
import org.navalplanner.business.common.exceptions.InstanceNotFoundException;
import org.navalplanner.business.common.exceptions.ValidationException;
import org.navalplanner.business.labels.daos.ILabelDAO;
import org.navalplanner.business.labels.entities.Label;
import org.navalplanner.business.orders.daos.IOrderDAO;
import org.navalplanner.business.orders.daos.IOrderElementDAO;
import org.navalplanner.business.orders.entities.HoursGroup;
import org.navalplanner.business.orders.entities.IOrderLineGroup;
import org.navalplanner.business.orders.entities.Order;
import org.navalplanner.business.orders.entities.OrderElement;
import org.navalplanner.business.orders.entities.OrderLineGroup;
import org.navalplanner.business.requirements.entities.DirectCriterionRequirement;
import org.navalplanner.business.resources.daos.ICriterionDAO;
import org.navalplanner.business.resources.daos.ICriterionTypeDAO;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.CriterionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Model for UI operations related to {@link Order}. <br />
 *
 * @author Óscar González Fernández <ogonzalez@igalia.com>
 * @author Diego Pino García <dpino@igalia.com>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class OrderModel implements IOrderModel {

    @Autowired
    private ICriterionTypeDAO criterionTypeDAO;

    private static final Map<CriterionType, List<Criterion>> mapCriterions = new HashMap<CriterionType, List<Criterion>>();

    @Autowired
    private IOrderDAO orderDAO;

    private Order order;

    private OrderElementTreeModel orderElementTreeModel;

    @Autowired
    private IOrderElementModel orderElementModel;

    @Autowired
    private ICriterionDAO criterionDAO;

    @Autowired
    private ILabelDAO labelDAO;

    @Autowired
    private IOrderElementDAO orderElementDAO;

    private Set<Label> cacheLabels = new HashSet<Label>();

    @Override
    public List<Label> getLabels() {
        final List<Label> result = new ArrayList<Label>();
        result.addAll(cacheLabels);
        return result;
    }

    @Override
    public void addLabel(Label label) {
        cacheLabels.add(label);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getOrders() {
        return orderDAO.getOrders();
    }

    private void loadCriterions() {
        mapCriterions.clear();
        List<CriterionType> criterionTypes = criterionTypeDAO
                .getCriterionTypes();
        for (CriterionType criterionType : criterionTypes) {
            List<Criterion> criterions = new ArrayList<Criterion>(criterionDAO
                    .findByType(criterionType));

            mapCriterions.put(criterionType, criterions);
        }
    }

    @Override
    public Map<CriterionType, List<Criterion>> getMapCriterions(){
        final Map<CriterionType, List<Criterion>> result =
                new HashMap<CriterionType, List<Criterion>>();
        result.putAll(mapCriterions);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public void initEdit(Order order) {
        Validate.notNull(order);
        initializeCacheLabels();
        loadCriterions();
        this.order = getFromDB(order);
        this.orderElementTreeModel = new OrderElementTreeModel(this.order);
        forceLoadAdvanceAssignmentsAndMeasurements(this.order);
        forceLoadCriterionRequirements(this.order);
    }

    private void initializeCacheLabels() {
        if (cacheLabels.isEmpty()) {
            cacheLabels = new HashSet<Label>();
            final List<Label> labels = labelDAO.getAll();
            initializeLabels(labels);
            cacheLabels.addAll(labels);
        }
    }

    private void initializeLabels(Collection<Label> labels) {
        for (Label label : labels) {
            initializeLabel(label);
        }
    }

    private void initializeLabel(Label label) {
        label.getName();
        label.getType().getName();
    }

    private static void forceLoadCriterionRequirements(OrderElement orderElement) {
        orderElement.getHoursGroups().size();
        for (HoursGroup hoursGroup : orderElement.getHoursGroups()) {
            attachDirectCriterionRequirement(hoursGroup
                    .getDirectCriterionRequirement());
        }
        attachDirectCriterionRequirement(orderElement
                .getDirectCriterionRequirement());

        for (OrderElement child : orderElement.getChildren()) {
            forceLoadCriterionRequirements(child);
        }
    }

    private static void attachDirectCriterionRequirement(
            Set<DirectCriterionRequirement> requirements) {
        for (DirectCriterionRequirement requirement : requirements) {
            requirement.getChildren().size();
            requirement.getCriterion().getName();
            requirement.getCriterion().getType().getName();
        }
    }

    private void forceLoadAdvanceAssignmentsAndMeasurements(
            OrderElement orderElement) {
        for (DirectAdvanceAssignment directAdvanceAssignment : orderElement
                .getDirectAdvanceAssignments()) {
            directAdvanceAssignment.getAdvanceType().getUnitName();
            for (AdvanceMeasurement advanceMeasurement : directAdvanceAssignment
                    .getAdvanceMeasurements()) {
                advanceMeasurement.getValue();
            }
        }

        if (orderElement instanceof OrderLineGroup) {
            for (IndirectAdvanceAssignment indirectAdvanceAssignment : ((OrderLineGroup) orderElement)
                    .getIndirectAdvanceAssignments()) {
                indirectAdvanceAssignment.getAdvanceType().getUnitName();
            }

            for (OrderElement child : orderElement.getChildren()) {
                forceLoadAdvanceAssignmentsAndMeasurements(child);
            }
        }
    }

    private Order getFromDB(Order order) {
        try {
            return orderDAO.find(order.getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void prepareForCreate() {
        loadCriterions();
        initializeCacheLabels();
        this.order = Order.create();
        this.orderElementTreeModel = new OrderElementTreeModel(this.order);
        this.order.setInitDate(new Date());
    }

    @Override
    @Transactional
    public void save() throws ValidationException {
        reattachCriterions();
        this.orderDAO.save(order);
        deleteOrderElementWithoutParent();
    }

    private void deleteOrderElementWithoutParent() throws ValidationException {
        List<OrderElement> listToBeRemoved = orderElementDAO
                .findWithoutParent();
        for (OrderElement orderElement : listToBeRemoved) {
            if (!(orderElement instanceof Order)) {
                try {
                    orderElementDAO.remove(orderElement.getId());
                } catch (InstanceNotFoundException e) {
                    throw new ValidationException(_(""
                            + "It not could remove the order element "
                            + orderElement.getName()));
                }
            }
        }
    }

    private void reattachCriterions() {
        for (List<Criterion> list : mapCriterions.values()) {
            for (Criterion criterion : list) {
                criterionDAO.reattachUnmodifiedEntity(criterion);
            }
        }
    }

    @Override
    public IOrderLineGroup getOrder() {
        return order;
    }

    @Override
    @Transactional
    public void remove(Order order) {
        try {
            this.orderDAO.remove(order.getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public OrderElementTreeModel getOrderElementTreeModel() {
        return orderElementTreeModel;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderElementTreeModel getOrderElementsFilteredByPredicate(
            IPredicate predicate) {
        // Iterate through orderElements from order
        List<OrderElement> orderElements = new ArrayList<OrderElement>();
        for (OrderElement orderElement : order.getOrderElements()) {
            reattachOrderElement(orderElement);
            reattachLabels();
            initializeLabels(orderElement.getLabels());
            // Accepts predicate, add it to list of orderElements
            if (predicate.accepts(orderElement)) {
                orderElements.add(orderElement);
            }
        }
        // Return list of filtered elements
        return new OrderElementTreeModel(order, orderElements);
    }

    private void reattachLabels() {
        for (Label label : cacheLabels) {
            labelDAO.reattach(label);
        }
    }

    private void reattachOrderElement(OrderElement orderElement) {
        orderElementDAO.save(orderElement);
    }

    @Override
    @Transactional(readOnly = true)
    public IOrderElementModel getOrderElementModel(OrderElement orderElement) {
        reattachCriterions();
        orderElementModel.setCurrent(orderElement, this);
        return orderElementModel;
    }

    @Override
    public List<Criterion> getCriterionsFor(CriterionType criterionType) {
        return mapCriterions.get(criterionType);
    }

    @Override
    public void setOrder(Order order) {
        this.order = order;
    }

}
