/*
 * Copyright 2003 - 2013 The eFaps Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Revision:        $Rev$
 * Last Changed:    $Date$
 * Last Changed By: $Author$
 */


package org.efaps.esjp.imex;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIImEx;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.imex.Import_Base.TERM;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("64cd44c4-608e-48ab-8caf-18fd49867429")
@EFapsRevision("$Rev$")
public abstract class CostCalculation_Base
{


    protected static final String PRODTEMPKEY = CostCalculation.class.getName() + ".Temporal.ProductKey";

    /**
     * @param _parameter    Parameter as passed by the eFasp API
     * @return Return containing html snipplet
     * @throws EFapsException on error
     */
    public Return getReportFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = getReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty("org.efaps.esjp.imex.CostCalculation.FileName"));
        final String html = dyRp.getHtmlSnipplet(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }


    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final AbstractDynamicReport dyRp = getReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty("org.efaps.esjp.imex.CostCalculation.FileName"));
        File file = null;
        if ("xls".equalsIgnoreCase((String) properties.get("Mime"))) {
            file = dyRp.getExcel(_parameter);
        } else if ("pdf".equalsIgnoreCase((String) properties.get("Mime"))) {
            file = dyRp.getPDF(_parameter);
        }
        ret.put(ReturnValues.VALUES, file);
        ret.put(ReturnValues.TRUE, true);
        return ret;
    }


    /**
     * @param _parameter    Parameter as passed by the eFasp API
     * @return the report class
     * @throws EFapsException on error
     */
    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new CostCalculationReport(_parameter);
    }

    protected Collection<Product> getProducts(final Parameter _parameter)
        throws EFapsException
    {
        final Map<Instance, Product> map = new HashMap<Instance,  Product>();

        final QueryBuilder attrQueryBldr = new QueryBuilder(CIImEx.Import2IncomingInvoice);
        attrQueryBldr.addWhereAttrEqValue(CIImEx.Import2IncomingInvoice.FromLink, _parameter.getInstance());
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CIImEx.Import2IncomingInvoice.ToLink);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionSumAbstract);
        queryBldr.addWhereAttrInQuery(CISales.PositionSumAbstract.DocumentAbstractLink, attrQuery);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selProdInst = SelectBuilder.get().linkto(CISales.PositionSumAbstract.Product).instance();
        final SelectBuilder selProdName = SelectBuilder.get().linkto(CISales.PositionSumAbstract.Product)
                        .attribute(CIProducts.ProductAbstract.Name);
        final SelectBuilder selProddDesc = SelectBuilder.get().linkto(CISales.PositionSumAbstract.Product)
                        .attribute(CIProducts.ProductAbstract.Description);
        multi.addSelect(selProdInst, selProdName, selProddDesc);
        multi.addAttribute(CISales.PositionSumAbstract.Quantity);
        multi.execute();
        while (multi.next()) {
            final Instance prodInst = multi.<Instance>getSelect(selProdInst);
            if (isProduct(_parameter, prodInst)) {
                if (!map.containsKey(prodInst)) {
                    final String prodName = multi.<String>getSelect(selProdName);
                    final String desc = multi.<String>getSelect(selProddDesc);
                    final Product prod = new Product(prodInst, prodName + " " + desc);
                    prod.addQuantity(multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.Quantity));
                    map.put(prodInst, prod);
                }
            }
        }
        return map.values();
    }

    protected boolean isProduct(final Parameter _parameter,
                                final Instance _prodInst)
    {
        return _prodInst.getType().isKindOf(CIProducts.StockProductAbstract.getType());
    }


    protected Map<Integer, Set<Instance>> getTermMapping(final Parameter _parameter)
        throws EFapsException
    {
        final Map<Integer, Set<Instance>> ret = new HashMap<Integer, Set<Instance>>();
        final QueryBuilder queryBldr = new QueryBuilder(CIImEx.ImportExport2DocumentAbstract);
        queryBldr.addWhereAttrEqValue(CIImEx.ImportExport2DocumentAbstract.FromLinkAbstract, _parameter.getInstance());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selDocInst = SelectBuilder.get().linkto(CIImEx.ImportExport2DocumentAbstract.ToLinkAbstract)
                        .instance();
        multi.addSelect(selDocInst);
        multi.addAttribute(CIImEx.ImportExport2DocumentAbstract.Term);
        multi.execute();
        while (multi.next()) {
            Integer termId = multi.<Integer>getAttribute(CIImEx.ImportExport2DocumentAbstract.Term);
            final Instance docInst = multi.<Instance>getSelect(selDocInst);
            if (termId == null) {
                termId = 0;
            }
            if (isCostDocument(_parameter, docInst)) {
                Set<Instance> set;
                if (ret.containsKey(termId)) {
                    set = ret.get(termId);
                } else {
                    set = new HashSet<Instance>();
                    ret.put(termId, set);
                }
                set.add(docInst);
            }
        }
        return ret;
    }


    protected boolean isCostDocument(final Parameter _parameter,
                                     final Instance _docInst)
    {
        return _docInst.getType().isKindOf(CISales.IncomingInvoice.getType());
    }

    /**
     * Report class.
     */
    public class CostCalculationReport
        extends AbstractDynamicReport
    {

        private final List<Product> products = new ArrayList<Product>();;

        public CostCalculationReport(final Parameter _parameter) throws EFapsException
        {
            this.products.addAll(getProducts(_parameter));
            Collections.sort(this.products, new Comparator<Product>()
            {
                @Override
                public int compare(final Product _o1,
                                   final Product _o2)
                {
                    return _o1.getLabel().compareTo(_o2.getLabel());
                }
            });
        }


        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final Collection<Map<String, ?>>dsList = new ArrayList<Map<String, ?>>();
            final Map<TERM, List<Map<String,Object>>> subListMap = new LinkedHashMap<TERM ,List<Map<String,Object>>>();
            final Map<Integer, Set<Instance>> termMapping = getTermMapping(_parameter);
            for (final TERM term : TERM.values()) {
                final Set<Instance> docInsts = termMapping.get(term.ordinal());
                if (docInsts != null) {
                    final List<Map<String,Object>> termList = new ArrayList<Map<String,Object>>();
                    final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionSumAbstract);
                    queryBldr.addWhereAttrEqValue(CISales.PositionSumAbstract.DocumentAbstractLink, docInsts.toArray());
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    final SelectBuilder selProdInst = SelectBuilder.get().linkto(CISales.PositionSumAbstract.Product)
                                    .instance();
                    multi.addSelect(selProdInst);
                    multi.addAttribute(CISales.PositionSumAbstract.CrossPrice);
                    multi.execute();
                    final Map<Instance, BigDecimal> costmap = new HashMap<Instance, BigDecimal>();
                    while (multi.next()) {
                        final Instance prodInst = multi.<Instance>getSelect(selProdInst);
                        if (!costmap.containsKey(prodInst)) {
                            costmap.put(prodInst, BigDecimal.ZERO);
                        }
                        final BigDecimal crossPrice = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.CrossPrice);
                        costmap.put(prodInst, costmap.get(prodInst).add(crossPrice));
                    }

                    // get the product line
                    final Map<String, Object> map = new HashMap<String, Object>();
                    int i = 0;
                    boolean add = false;
                    BigDecimal prodTotal = BigDecimal.ZERO;
                    for (final Product product: this.products) {
                        if (costmap.containsKey(product.getInstance())) {
                            final BigDecimal cost = costmap.get(product.getInstance());
                            prodTotal = prodTotal.add(cost);
                            map.put("product" + i, cost);
                            product.addCost(cost);
                            add = true;
                        } else {
                            map.put("product" + i, BigDecimal.ZERO);
                        }
                        i++;
                    }
                    if (add) {
                        map.put("descr", CostCalculation_Base.PRODTEMPKEY);
                        map.put("expense", prodTotal);
                        termList.add(map);
                    }

                    for (final Entry<Instance, BigDecimal> entry : costmap.entrySet()) {
                        if (!isProduct(_parameter, entry.getKey())) {
                            final Map<String, Object> tmpMap = new HashMap<String, Object>();
                            final PrintQuery print = new PrintQuery(entry.getKey());
                            print.addAttribute(CIProducts.ProductAbstract.Name);
                            print.execute();
                            tmpMap.put("expense", entry.getValue());
                            tmpMap.put("descr", print.getAttribute(CIProducts.ProductAbstract.Name));
                            termList.add(tmpMap);
                        }
                    }
                    subListMap.put(term, termList);
                }
            }
            BigDecimal productTotal = BigDecimal.ZERO;
            for (final Product product : this.products) {
                productTotal = productTotal.add(product.cost);
            }


            for (final Entry<TERM, List<Map<String,Object>>> entry  : subListMap.entrySet()) {
                for (final Map<String,Object> map : entry.getValue()) {
                    // is this a product line
                    if (CostCalculation_Base.PRODTEMPKEY.equals(map.get("descr"))) {
                        map.put("descr", getDBProp("ProductValue"));
                    } else {
                        int i = 0;
                        for (final Product product : this.products) {
                            map.put("product" + i, product.getPartial((BigDecimal) map.get("expense"),  productTotal));
                            i++;
                        }
                    }
                    dsList.add(map);
                }

                // summarize up to this line
                final Map<String, Object> tmpMap = new HashMap<String, Object>();
                tmpMap.put("expense", BigDecimal.ZERO);
                tmpMap.put("descr", entry.getKey().toString());
                tmpMap.put("summarize", false);
                for (int i = 0; i < this.products.size(); i++) {
                    tmpMap.put("product" + i, BigDecimal.ZERO);
                }

                for (final Map<String, ?> map : dsList) {
                    if (!map.containsKey("summarize")) {
                        tmpMap.put("expense", ((BigDecimal) tmpMap.get("expense")).add((BigDecimal) map.get("expense")));
                        for (int i = 0; i < this.products.size(); i++) {
                            tmpMap.put("product" + i, ((BigDecimal) tmpMap.get("product" + i)).add((BigDecimal) map
                                            .get("product" + i)));
                        }
                    }
                }
                dsList.add(tmpMap);
            }

            // summarize over all to get the WareHouseCost
            final Map<String, Object> tmpMap = new HashMap<String, Object>();
            tmpMap.put("expense", BigDecimal.ZERO);
            tmpMap.put("descr", getDBProp("WareHouseCost"));
            tmpMap.put("summarize", false);
            for (int i = 0; i < this.products.size(); i++) {
                tmpMap.put("product" + i, BigDecimal.ZERO);
            }
            for (final Map<String, ?> map : dsList) {
                if (!map.containsKey("summarize")) {
                    tmpMap.put("expense", ((BigDecimal) tmpMap.get("expense")).add((BigDecimal) map.get("expense")));
                    for (int i = 0; i < this.products.size(); i++) {
                        tmpMap.put("product" + i, ((BigDecimal) tmpMap.get("product" + i)).add((BigDecimal) map
                                        .get("product" + i)));
                    }
                }
            }
            dsList.add(tmpMap);

            // get the cost per product
            final Map<String, Object> tmpMap2 = new HashMap<String, Object>();
            tmpMap2.put("expense", BigDecimal.ZERO);
            tmpMap2.put("descr", getDBProp("WareHouseCostPerUnit"));

            int i = 0;
            for (final Product product : this.products) {
                final BigDecimal productUnitTotal = (BigDecimal) tmpMap.get("product" + i);
                tmpMap2.put("product" + i,
                                productUnitTotal.setScale(12).divide(product.getQuantity(), BigDecimal.ROUND_HALF_UP)
                                                .setScale(2));
                i++;
            }
            dsList.add(tmpMap2);
            final JRMapCollectionDataSource ret = new JRMapCollectionDataSource(dsList);
            return ret;
        }

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {

            final TextColumnBuilder<String> descrColumn = DynamicReports.col.column(getDBProp("Column.descr"), "descr",
                            DynamicReports.type.stringType());
            descrColumn.setWidth(200);

            final TextColumnBuilder<BigDecimal> expenseColumn = DynamicReports.col.column(getDBProp("Column.expense"),
                            "expense", DynamicReports.type.bigDecimalType());

            _builder.addColumn(descrColumn, expenseColumn);
            int i = 0;
            for (final Product product : this.products) {
                final TextColumnBuilder<BigDecimal> prodColumn = DynamicReports.col.column(product.getLabel(),
                                "product" + i, DynamicReports.type.bigDecimalType());
                _builder.addColumn(prodColumn);
                i++;
            }
        }


        protected String getDBProp(final String _key)
        {
            return DBProperties.getProperty(CostCalculation.class.getName() + "." + _key);
        }

    }


    public static class Product
    {
        private final Instance instance;
        private final String label;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal cost = BigDecimal.ZERO;

        public Product(final Instance _instance,
                       final String _label)
        {
            this.instance = _instance;
            this.label = _label;
        }


        /**
         * @param _bigDecimal
         * @param _productTotal
         */
        public BigDecimal getPartial(final BigDecimal _expense,
                                     final BigDecimal _productTotal)
        {
            BigDecimal ret = _expense.setScale(12).divide(_productTotal, BigDecimal.ROUND_HALF_DOWN).multiply(this.cost);
            ret = ret.setScale(2,  BigDecimal.ROUND_HALF_DOWN);
            return ret;

        }


        /**
         * Getter method for the instance variable {@link #instance}.
         *
         * @return value of instance variable {@link #instance}
         */
        public Instance getInstance()
        {
            return this.instance;
        }

        /**
         * Getter method for the instance variable {@link #label}.
         *
         * @return value of instance variable {@link #label}
         */
        public String getLabel()
        {
            return this.label;
        }

        public void addCost(final BigDecimal _cost)
        {
            this.cost = this.cost.add(_cost);
        }

        public void addQuantity(final BigDecimal _quantity)
        {
            this.quantity = this.quantity.add(_quantity);
        }



        /**
         * Getter method for the instance variable {@link #quantity}.
         *
         * @return value of instance variable {@link #quantity}
         */
        public BigDecimal getQuantity()
        {
            return this.quantity.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : this.quantity;
        }


    }
}
