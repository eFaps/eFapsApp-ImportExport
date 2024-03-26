/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
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

import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
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

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.VariableBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.style.ConditionalStyleBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.constant.Calculation;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * eFapsApp-Sales
 */
@EFapsUUID("64cd44c4-608e-48ab-8caf-18fd49867429")
@EFapsApplication("eFapsApp-ImportExport")
public abstract class CostCalculation_Base
{

    /**
     * Key used to store temporary values in the map of data.
     */
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
        dyRp.setFileName(getDBProp("FileName"));
        final String html = dyRp.getHtmlSnipplet(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    /**
     * @param _parameter    Parameter as passed by the eFasp API
     * @return Return containing the file
     * @throws EFapsException on error
     */
    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final AbstractDynamicReport dyRp = getReport(_parameter);
        dyRp.setFileName(getDBProp("FileName"));
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

    /**
     * @param _parameter  Parameter as passed by the eFasp API
     * @return list of products used in the data map
     * @throws EFapsException on error
     */
    protected Collection<Product> getProducts(final Parameter _parameter)
        throws EFapsException
    {
        final Map<Instance, Product> map = new HashMap<>();

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
        multi.addAttribute(CISales.PositionSumAbstract.Quantity, CISales.PositionSumAbstract.UoM);
        multi.execute();
        while (multi.next()) {
            final Instance prodInst = multi.<Instance>getSelect(selProdInst);
            if (isProduct(_parameter, prodInst)) {
                BigDecimal quantity = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.Quantity);
                final UoM uoM = Dimension.getUoM(multi.<Long>getAttribute(CISales.PositionSumAbstract.UoM));
                if (!uoM.getDimension().getBaseUoM().equals(uoM)) {
                    quantity = new BigDecimal(uoM.getNumerator()).setScale(12, BigDecimal.ROUND_HALF_UP)
                                    .divide(new BigDecimal(uoM.getDenominator()), BigDecimal.ROUND_HALF_UP)
                                        .multiply(quantity);
                }
                if (map.containsKey(prodInst)) {
                    map.get(prodInst).addQuantity(quantity);
                } else {
                    final String prodName = multi.<String>getSelect(selProdName);
                    final String desc = multi.<String>getSelect(selProddDesc);
                    final Product prod = new Product(prodInst, prodName + " " + desc);
                    prod.addQuantity(quantity);
                    map.put(prodInst, prod);
                }
            }
        }
        return map.values();
    }

    /**
     * @param _parameter  Parameter as passed by the eFasp API
     * @return true if the product is a product and not a service in costing
     * @throws EFapsException on error
     */
    protected boolean isProduct(final Parameter _parameter,
                                final Instance _prodInst)
        throws EFapsException
    {
        return _prodInst.getType().isKindOf(CIProducts.StoreableProductAbstract.getType());
    }

    /**
     * @param _parameter  Parameter as passed by the eFasp API
     * @return mapping of terms to Documents
     * @throws EFapsException on error
     */
    protected Map<Integer, Set<Doc>> getTermMapping(final Parameter _parameter)
        throws EFapsException
    {
        final Map<Integer, Set<Doc>> ret = new HashMap<>();
        final QueryBuilder queryBldr = new QueryBuilder(CIImEx.ImportExport2DocumentAbstract);
        queryBldr.addWhereAttrEqValue(CIImEx.ImportExport2DocumentAbstract.FromLinkAbstract, _parameter.getInstance());
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selDocInst = SelectBuilder.get().linkto(CIImEx.ImportExport2DocumentAbstract.ToLinkAbstract)
                        .instance();
        multi.addSelect(selDocInst);
        multi.addAttribute(CIImEx.ImportExport2DocumentAbstract.Term, CIImEx.ImportExport2DocumentAbstract.Numerator,
                        CIImEx.ImportExport2DocumentAbstract.Denominator);
        multi.execute();
        while (multi.next()) {
            Integer termId = multi.<Integer>getAttribute(CIImEx.ImportExport2DocumentAbstract.Term);
            final Instance docInst = multi.<Instance>getSelect(selDocInst);
            if (termId == null) {
                termId = 0;
            }
            if (isCostDocument(_parameter, docInst)) {
                Set<Doc> set;
                if (ret.containsKey(termId)) {
                    set = ret.get(termId);
                } else {
                    set = new HashSet<>();
                    ret.put(termId, set);
                }
                set.add(new Doc(docInst, multi.<Integer>getAttribute(CIImEx.ImportExport2DocumentAbstract.Numerator),
                                multi.<Integer>getAttribute(CIImEx.ImportExport2DocumentAbstract.Denominator)) );
            }
        }
        return ret;
    }

    /**
     * @param _parameter  Parameter as passed by the eFasp API
     * @return true if the document must be included in the costing
     * @throws EFapsException on error
     */
    protected boolean isCostDocument(final Parameter _parameter,
                                     final Instance _docInst)
    {
        return _docInst.getType().isKindOf(CISales.IncomingInvoice.getType());
    }

    /**
     * @param _parameter  Parameter as passed by the eFasp API
     * @return data map as presented by the report
     * @throws EFapsException on error
     */
    protected Collection<Map<String, ?>> getDataMap(final Parameter _parameter,
                                                    final List<Product> _products)
        throws EFapsException
    {
        final Collection<Map<String, ?>> dsList = new ArrayList<>();
        final Map<TERM, List<Map<String, Object>>> subListMap = new LinkedHashMap<>();
        final Map<Integer, Set<Doc>> termMapping = getTermMapping(_parameter);
        for (final TERM term : TERM.values()) {
            final Set<Doc> docInsts = termMapping.get(term.ordinal());
            if (docInsts != null) {
                final Map<Instance, Doc> docMap = new HashMap<>();
                for (final Doc doc : docInsts) {
                    docMap.put(doc.getInstance(), doc);
                }
                final List<Map<String, Object>> termList = new ArrayList<>();
                final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionSumAbstract);
                queryBldr.addWhereAttrEqValue(CISales.PositionSumAbstract.DocumentAbstractLink,
                                docMap.keySet().toArray());
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selProdInst = SelectBuilder.get().linkto(CISales.PositionSumAbstract.Product)
                                .instance();
                final SelectBuilder selDocInst = SelectBuilder.get().linkto(
                                CISales.PositionSumAbstract.DocumentAbstractLink).instance();
                multi.addSelect(selProdInst, selDocInst);
                multi.addAttribute(CISales.PositionSumAbstract.NetPrice);
                multi.execute();
                final Map<Instance, BigDecimal> costmap = new HashMap<>();
                while (multi.next()) {
                    final Instance prodInst = multi.<Instance>getSelect(selProdInst);
                    final Instance docInst = multi.<Instance>getSelect(selDocInst);
                    if (!costmap.containsKey(prodInst)) {
                        costmap.put(prodInst, BigDecimal.ZERO);
                    }
                    final BigDecimal price = multi.<BigDecimal>getAttribute(CISales.PositionSumAbstract.NetPrice);
                    final Doc doc = docMap.get(docInst);
                    costmap.put(prodInst, costmap.get(prodInst).add(doc.getCost(price)));
                }

                // get the product line
                final Map<String, Object> map = new HashMap<>();
                int i = 0;
                boolean add = false;
                BigDecimal prodTotal = BigDecimal.ZERO;
                for (final Product product : _products) {
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
                        final Map<String, Object> tmpMap = new HashMap<>();
                        final PrintQuery print = new PrintQuery(entry.getKey());
                        print.addAttribute(CIProducts.ProductAbstract.Name, CIProducts.ProductAbstract.Description);
                        print.execute();
                        tmpMap.put("expense", entry.getValue());
                        tmpMap.put("descr", print.getAttribute(CIProducts.ProductAbstract.Name) + " " +
                                        print.getAttribute(CIProducts.ProductAbstract.Description));
                        termList.add(tmpMap);
                    }
                }
                subListMap.put(term, termList);
            }
        }
        BigDecimal productTotal = BigDecimal.ZERO;
        for (final Product product : _products) {
            productTotal = productTotal.add(product.cost);
        }

        for (final Entry<TERM, List<Map<String, Object>>> entry : subListMap.entrySet()) {
            for (final Map<String, Object> map : entry.getValue()) {
                // is this a product line
                if (CostCalculation_Base.PRODTEMPKEY.equals(map.get("descr"))) {
                    map.put("descr", getDBProp("ProductValue"));
                } else {
                    int i = 0;
                    for (final Product product : _products) {
                        map.put("product" + i, product.getPartial((BigDecimal) map.get("expense"), productTotal));
                        i++;
                    }
                }
                map.put("summary", false);
                dsList.add(map);
            }

            // summarize up to this line
            final Map<String, Object> tmpMap = new HashMap<>();
            tmpMap.put("expense", BigDecimal.ZERO);
            tmpMap.put("descr", entry.getKey().toString());
            tmpMap.put("summary", true);
            for (int i = 0; i < _products.size(); i++) {
                tmpMap.put("product" + i, BigDecimal.ZERO);
            }

            for (final Map<String, ?> map : dsList) {
                if (!(Boolean) map.get("summary")) {
                    tmpMap.put("expense", ((BigDecimal) tmpMap.get("expense")).add((BigDecimal) map.get("expense")));
                    for (int i = 0; i < _products.size(); i++) {
                        tmpMap.put("product" + i, ((BigDecimal) tmpMap.get("product" + i)).add((BigDecimal) map
                                        .get("product" + i)));
                    }
                }
            }
            dsList.add(tmpMap);
        }

        // summarize over all to get the WareHouseCost
        final Map<String, Object> tmpMap = new HashMap<>();
        tmpMap.put("expense", BigDecimal.ZERO);
        tmpMap.put("descr", getDBProp("WareHouseCost"));
        tmpMap.put("summary", true);
        for (int i = 0; i < _products.size(); i++) {
            tmpMap.put("product" + i, BigDecimal.ZERO);
        }
        for (final Map<String, ?> map : dsList) {
            if (!(Boolean) map.get("summary")) {
                tmpMap.put("expense", ((BigDecimal) tmpMap.get("expense")).add((BigDecimal) map.get("expense")));
                for (int i = 0; i < _products.size(); i++) {
                    tmpMap.put("product" + i, ((BigDecimal) tmpMap.get("product" + i)).add((BigDecimal) map
                                    .get("product" + i)));
                }
            }
        }
        dsList.add(tmpMap);

        // get the cost per product
        final Map<String, Object> tmpMap2 = new HashMap<>();
        tmpMap2.put("expense", BigDecimal.ZERO);
        tmpMap2.put("descr", getDBProp("WareHouseCostPerUnit"));
        tmpMap2.put("summary", true);
        int i = 0;
        for (final Product product : _products) {
            final BigDecimal productUnitTotal = (BigDecimal) tmpMap.get("product" + i);
            tmpMap2.put("product" + i,
                            productUnitTotal.setScale(12, BigDecimal.ROUND_HALF_UP)
                                            .divide(product.getQuantity(), BigDecimal.ROUND_HALF_UP)
                                            .setScale(2, BigDecimal.ROUND_HALF_UP));
            i++;
        }
        dsList.add(tmpMap2);
        return dsList;
    }

    /**
     * @param _parameter  Parameter as passed by the eFasp API
     * @return a value for the key
     * @throws EFapsException on error
     */
    protected String getDBProp(final String _key)
        throws EFapsException
    {
        return DBProperties.getProperty(CostCalculation.class.getName() + "." + _key);
    }


    /**
     * Report class.
     */
    public class CostCalculationReport
        extends AbstractDynamicReport
    {

        private final List<Product> products = new ArrayList<>();;

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
            final JRMapCollectionDataSource ret = new JRMapCollectionDataSource(getDataMap(_parameter, this.products));
            return ret;
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final ConditionalStyleBuilder condition1 = DynamicReports.stl.conditionalStyle(new IsSumExpression())
                            .setBold(true);
            final StyleBuilder stBldr;
            switch (getExType()) {
                case PDF:
                    stBldr = getColumnStyle4Pdf(_parameter);
                    break;
                case EXCEL:
                    stBldr = getColumnStyle4Excel(_parameter);
                    break;
                case HTML:
                    stBldr = getColumnStyle4Html(_parameter);
                    break;
                default:
                    stBldr = DynamicReports.stl.style();
                    break;
            }
            stBldr.conditionalStyles(condition1);

            final TextColumnBuilder<String> descrColumn = DynamicReports.col.column(getDBProp("Column.descr"), "descr",
                            DynamicReports.type.stringType());
            descrColumn.setWidth(200);
            descrColumn.setStyle(stBldr);
            final TextColumnBuilder<BigDecimal> expenseColumn = DynamicReports.col.column(getDBProp("Column.expense"),
                            "expense", DynamicReports.type.bigDecimalType());
            expenseColumn.setStyle(stBldr);
            final VariableBuilder<Boolean> summary = DynamicReports.variable("summary", Boolean.class,
                            Calculation.NOTHING);
            _builder.addVariable(summary);

            _builder.addColumn(descrColumn, expenseColumn);
            int i = 0;
            for (final Product product : this.products) {
                final TextColumnBuilder<BigDecimal> prodColumn = DynamicReports.col.column(product.getLabel(),
                                "product" + i, DynamicReports.type.bigDecimalType());
                prodColumn.setStyle(stBldr);
                _builder.addColumn(prodColumn);
                i++;
            }
        }
    }


    /**
     * Class represents one Document Instacne and the relation defintion.
     */
    public static class Doc
    {

        /**
         * Instance of this document.
         */
        private final Instance instance;

        /**
         * Numerator to be applied on this document.
         */
        private final Integer numerator;

        /**
         * Denominator to be applied on this document.
         */
        private final Integer denominator;

        /**
         * @param _instance Intance
         * @param _numerator Numerator
         * @param _denominator Denominator
         */
        public Doc(final Instance _instance,
                   final Integer _numerator,
                   final Integer _denominator)
        {
            this.instance = _instance;
            this.numerator = _numerator;
            this.denominator = _denominator;
        }

        /**
         * @param _price Price
         * @return price cost
         */
        public BigDecimal getCost(final BigDecimal _cost)
        {
            final BigDecimal ret;
            if (this.numerator.equals(this.denominator)) {
                ret = _cost;
            } else {
                ret = new BigDecimal(this.numerator).setScale(12, BigDecimal.ROUND_HALF_UP)
                    .divide( new BigDecimal(this.denominator), BigDecimal.ROUND_HALF_UP)
                    .multiply(_cost)
                    .setScale(2, BigDecimal.ROUND_HALF_UP);
            }
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
    }

    /**
     * A product including related infos.
     */
    public static class Product
    {
        /**
         * Instance this class belongs to.
         */
        private final Instance instance;

        /**
         * Label for this product.
         */
        private final String label;

        /**
         * Quantity.
         */
        private BigDecimal quantity = BigDecimal.ZERO;
        /**
         * Cost.
         */
        private BigDecimal cost = BigDecimal.ZERO;

        /**
         * @param _instance Instance
         * @param _label    Label
         */
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
            BigDecimal ret = _expense.setScale(12, BigDecimal.ROUND_HALF_UP)
                            .divide(_productTotal, BigDecimal.ROUND_HALF_UP).multiply(this.cost);
            ret = ret.setScale(2,  BigDecimal.ROUND_HALF_UP);
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

        /**
         * @param _cost cost to be added
         */
        public void addCost(final BigDecimal _cost)
        {
            this.cost = this.cost.add(_cost);
        }

        /**
         * @param _quantity Quantity to be added
         */
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

    /**
     * Expression used for StyleSheet.
     */
    public static class IsSumExpression
        extends AbstractSimpleExpression<Boolean>
    {
        private static final long serialVersionUID = 1L;

        @Override
        public Boolean evaluate(final ReportParameters _reportParameters)
        {
            final Boolean summarize = _reportParameters.getFieldValue("summary");
            return summarize;
        }
    }
}
