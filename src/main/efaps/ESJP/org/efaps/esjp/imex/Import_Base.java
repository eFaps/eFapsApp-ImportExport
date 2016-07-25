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

import java.util.UUID;

import org.efaps.admin.common.NumberGenerator;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.Insert;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIImEx;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uiform.Create;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * eFapsApp-Sales
 */
@EFapsUUID("8043bb61-7008-425f-b1f6-8300ac580e3c")
@EFapsApplication("eFapsApp-ImportExport")
public abstract class Import_Base
{

    /**
     * Enum for Incoterm-Codes 2010.
     */
    public enum TERM
    {
        /** EX Works. */
        EXW,
        /** Free CArrier. */
        FCA,
        /**  Free Alongside Ship. */
        FAS,
        /** Free On Board. */
        FOB,
        /**  Cost And FReight. */
        CFR,
        /** Cost Insurance Freight. */
        CIF,
        /** Delivered At Terminal. */
        DAT,
        /**  Delivered At Place. */
        DAP,
        /** Carriage Paid To. */
        CPT,
        /** Carriage Insurance Paid. */
        CIP,
        /** Delivered Duty Paid. */
        DDP;
    }


    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Create create = new Create()
        {

            @Override
            protected void add2basicInsert(final Parameter _parameter,
                                           final Insert _insert)
                throws EFapsException
            {
                // ImEx_ImportSequence
                final String name = NumberGenerator.get(UUID.fromString("2f0e1989-3170-4226-a1c3-7aae7e532ff1"))
                                .getNextVal();
                _insert.add(CIImEx.Import.Name, name);
            }
        };
        return create.execute(_parameter);
    }


    public Return documentMultiPrint(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final QueryBuilder ooAttrQueryBldr = new QueryBuilder(CIImEx.Import2OrderOutbound);
                final AttributeQuery ooAttrQuery = ooAttrQueryBldr
                                .getAttributeQuery(CIImEx.Import2OrderOutbound.ToLink);

                final QueryBuilder docAttrQueryBldr = new QueryBuilder(CISales.Document2DocumentAbstract);
                docAttrQueryBldr.addWhereAttrInQuery(CISales.Document2DocumentAbstract.FromAbstractLink, ooAttrQuery);
                final AttributeQuery docAttrQuery = docAttrQueryBldr
                                .getAttributeQuery(CISales.Document2DocumentAbstract.ToAbstractLink);

                final QueryBuilder connAttrQueryBldr = new QueryBuilder(CIImEx.ImportExport2DocumentAbstract);
                connAttrQueryBldr.addWhereAttrEqValue(CIImEx.ImportExport2DocumentAbstract.FromLinkAbstract,
                                _parameter.getInstance());
                final AttributeQuery connAttrQuery = connAttrQueryBldr
                                .getAttributeQuery(CIImEx.ImportExport2DocumentAbstract.ToLinkAbstract);

                _queryBldr.addWhereAttrInQuery(CISales.DocumentAbstract.ID, docAttrQuery);
                _queryBldr.addWhereAttrNotInQuery(CISales.DocumentAbstract.ID, connAttrQuery);
            }
        };
        return multi.execute(_parameter);
    }
}
